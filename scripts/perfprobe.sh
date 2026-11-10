#!/usr/bin/env bash
# perfprobe.sh — ADB-driven CPU/frame/energy harness for game-twin vault session tabs.
set -euo pipefail

PACKAGE_DEFAULT="com.twinlore.app"
DURATION_DEFAULT=30
OUT_DIR="perf-runs"
CSV_FILE="${OUT_DIR}/perf-runs.csv"
CSV_HEADER="timestamp,label,scenario,duration_s,mAh,cpu_pct_top,cpu_s_proc,total_frames,janky_frames,jank_pct,p50_ms,p90_ms,p95_ms,p99_ms,notes"

LABEL=""
SCENARIO=""
DURATION="$DURATION_DEFAULT"
PACKAGE="$PACKAGE_DEFAULT"
SKIP_CHECKS=0

usage() {
    cat >&2 <<EOF
Usage: $0 --label NAME --scenario SCENARIO [--duration SECONDS] [--package PKG] [--skip-checks]

Scenarios:
  idle-tab        Sit idle. Measures background work on the current screen.
  button-spam     KEYCODE_BUTTON_X every 200ms. Tests controllerAction fanout.
  tab-cycle       L1/R1 alternating every 1000ms. Tests tab activation cost.
  scroll-spam     DPAD_DOWN every 100ms. Tests scroll-driven recomposition.
  nav-paced       DPAD_RIGHT every 400ms. Tests per-move cost at browsing pace.

Options:
  --label NAME         Identifier for this run (e.g. baseline, fix-controller-gate)
  --scenario SCENARIO  One of the scenarios above
  --duration SECONDS   How long to run the scenario (default: ${DURATION_DEFAULT})
  --package PKG        Package id under test (default: ${PACKAGE_DEFAULT})
  --skip-checks        Skip foreground + battery-level preflight (unplug check is mandatory)

Open the app and navigate to the screen you want to test BEFORE running this script.
EOF
    exit 2
}

require_value() {
    [ $# -ge 2 ] || { echo "Missing value for $1" >&2; usage; }
}

while [ $# -gt 0 ]; do
    case "$1" in
        --label) require_value "$@"; LABEL="$2"; shift 2 ;;
        --scenario) require_value "$@"; SCENARIO="$2"; shift 2 ;;
        --duration) require_value "$@"; DURATION="$2"; shift 2 ;;
        --package) require_value "$@"; PACKAGE="$2"; shift 2 ;;
        --skip-checks) SKIP_CHECKS=1; shift ;;
        -h|--help) usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

[ -z "$LABEL" ] && { echo "Missing --label" >&2; usage; }
[ -z "$SCENARIO" ] && { echo "Missing --scenario" >&2; usage; }
case "$SCENARIO" in
    idle-tab|button-spam|tab-cycle|scroll-spam|nav-paced) ;;
    *) echo "Unknown scenario: $SCENARIO" >&2; usage ;;
esac
case "$LABEL" in
    *,*|*\"*|*$'\n'*) echo "--label cannot contain commas, quotes, or newlines (would corrupt the CSV)" >&2; exit 2 ;;
esac
case "$DURATION" in
    ''|*[!0-9]*) echo "--duration must be a positive integer" >&2; exit 2 ;;
esac
[ "$DURATION" -lt 1 ] && { echo "--duration must be >= 1" >&2; exit 2; }

log()    { printf "[perfprobe] %s\n" "$*" >&2; }
log_err(){ printf "[perfprobe][ERROR] %s\n" "$*" >&2; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || {
        log_err "$1 not found on PATH. Install Android Platform Tools and retry."
        exit 3
    }
}

assert_single_device() {
    local devices
    devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    local count
    count=$(printf "%s\n" "$devices" | sed '/^$/d' | wc -l | tr -d ' ')
    if [ "$count" -eq 0 ]; then
        log_err "No adb device connected. Plug in / authorise the device."
        exit 3
    fi
    if [ "$count" -gt 1 ]; then
        log_err "Multiple adb devices connected. Use ANDROID_SERIAL or disconnect extras."
        exit 3
    fi
}

assert_foreground() {
    # On multi-display devices each display has its own focused app, so check ANY
    # focus line for the package rather than just the first.
    local focus
    focus=$(adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | grep "$PACKAGE" || true)
    if [ -z "$focus" ]; then
        log_err "$PACKAGE is not the foreground app on any display."
        log_err "  Open the app and navigate to the screen you want to measure, then retry."
        exit 3
    fi
}

assert_battery_level() {
    local level
    level=$(adb shell dumpsys battery | awk -F': ' '/^[[:space:]]*level:/ {gsub(/ /,"",$2); print $2; exit}')
    if [ -z "$level" ]; then
        log_err "Could not read battery level via dumpsys."
        exit 3
    fi
    if [ "$level" -lt 30 ]; then
        log_err "Battery level is ${level}% (< 30%). Results will be skewed by power-saving."
        log_err "Charge the device and retry, or pass --skip-checks."
        exit 3
    fi
    log "battery level: ${level}%"
}

PLUGGED_AT_START=0

is_plugged() {
    adb shell dumpsys battery | grep -qE 'AC powered: true|USB powered: true|Wireless powered: true'
}

fake_unplug() {
    if is_plugged; then
        log "device is plugged in; faking unplug for the run window"
        adb shell dumpsys battery unplug >/dev/null
        PLUGGED_AT_START=1
    fi
}

restore_plug() {
    if [ "$PLUGGED_AT_START" -eq 1 ]; then
        log "restoring battery state (dumpsys battery reset)"
        adb shell dumpsys battery reset >/dev/null || true
    fi
}

reset_counters() {
    log "resetting batterystats + gfxinfo"
    adb shell dumpsys batterystats --reset >/dev/null
    adb shell "dumpsys gfxinfo $PACKAGE reset" >/dev/null
}

get_pid() {
    adb shell pidof "$PACKAGE" 2>/dev/null | awk '{print $1}'
}

get_uid_num() {
    # Samsung One UI / newer Android: `appId=10647`. Older: `userId=10647`.
    # Match either, anchored to leading whitespace to avoid matching `userId=`
    # tokens inside permission-grant / package-user-state blobs.
    adb shell "dumpsys package $PACKAGE" \
        | awk -F= '/^[[:space:]]*(userId|appId)=/ {print $2; exit}' \
        | tr -d '\r'
}

format_short_uid() {
    local uid="$1"
    case "$uid" in
        ''|*[!0-9]*) echo "uid:${uid:-unknown}"; return ;;
    esac
    if [ "$uid" -ge 10000 ]; then
        echo "u0a$((uid - 10000))"
    else
        echo "uid:$uid"
    fi
}

# Walk dumpsys window's Display blocks looking for the one whose mCurrentFocus
# belongs to $PACKAGE. On multi-display devices `input keyevent` without -d
# targets display 0, which may not be where the app is actually drawing.
get_app_display_id() {
    adb shell dumpsys window 2>/dev/null | awk -v pkg="$PACKAGE" '
        /mDisplayId=/ {
            if (match($0, /mDisplayId=[0-9]+/)) {
                current = substr($0, RSTART + 11, RLENGTH - 11)
            }
        }
        $0 ~ "mCurrentFocus.*"pkg {
            if (current != "") { print current; exit }
        }
    '
}

TOP_LOG=""
TOP_PID=""

start_top_sampler() {
    local pid="$1"
    TOP_LOG=$(mktemp -t perfprobe-top.XXXXXX)
    # -b batch mode, -n iterations, -d delay seconds, -p restrict to pid
    adb shell "top -b -p $pid -n $DURATION -d 1" > "$TOP_LOG" 2>/dev/null &
    TOP_PID=$!
}

stop_top_sampler() {
    if [ -n "$TOP_PID" ]; then
        wait "$TOP_PID" 2>/dev/null || true
    fi
}

parse_top_avg_cpu() {
    local pid="$1"
    # toybox top column order: PID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ ARGS  -> field 9
    awk -v pid="$pid" '
        $1 == pid {
            # %CPU may carry no decimal; strip non-numeric just in case
            v = $9
            gsub(/[^0-9.]/, "", v)
            if (v != "") { s += v; n++ }
        }
        END {
            if (n > 0) printf "%.2f", s / n
            else printf ""
        }
    ' "$TOP_LOG"
}

CLK_TCK=""

read_proc_stat_jiffies() {
    local pid="$1"
    # /proc/$pid/stat field 14 = utime, field 15 = stime (in clock ticks)
    # The 2nd field is "(comm)" which can contain spaces, so we slice from the last ')'.
    adb shell "cat /proc/$pid/stat" 2>/dev/null \
        | awk '{
            line = $0
            close_idx = index(line, ") ")
            tail = substr(line, close_idx + 2)
            n = split(tail, parts, " ")
            # tail field 1 = state, so utime is at index 12, stime at 13
            utime = parts[12]
            stime = parts[13]
            printf "%d %d", utime + 0, stime + 0
        }'
}

get_clk_tck() {
    if [ -z "$CLK_TCK" ]; then
        CLK_TCK=$(adb shell "getconf CLK_TCK" 2>/dev/null | tr -d '\r')
        [ -z "$CLK_TCK" ] && CLK_TCK=100
    fi
    echo "$CLK_TCK"
}

GFXINFO_RAW=""

collect_gfxinfo() {
    GFXINFO_RAW=$(adb shell "dumpsys gfxinfo $PACKAGE framestats" 2>/dev/null || true)
}

parse_gfx_total_frames() {
    # Herestring (not pipe) so awk's early `exit` can't SIGPIPE the upstream
    # writer under `set -o pipefail`. Same fix below.
    awk '/Total frames rendered:/ {print $4; exit}' <<< "$GFXINFO_RAW"
}

parse_gfx_janky_frames() {
    awk '/Janky frames:/ {print $3; exit}' <<< "$GFXINFO_RAW"
}

parse_gfx_percentile() {
    local label="$1"   # e.g. "50th"
    awk -v lbl="$label" '
        $0 ~ lbl " percentile:" {
            v = $3
            gsub(/ms/, "", v)
            print v
            exit
        }
    ' <<< "$GFXINFO_RAW"
}

BATTERYSTATS_RAW=""

collect_batterystats() {
    BATTERYSTATS_RAW=$(adb shell "dumpsys batterystats --charged" 2>/dev/null || true)
}

parse_mah_for_uid() {
    local short_uid="$1"
    # Lines look like: "Uid u0a142: 6.50 ( cpu=4.20 ... )"
    # Some Android versions render as "    Uid u0a142: 6.50".
    # Grab the first decimal token after the colon. Some devices report
    # "Not supported on this device" — grep exits 1 there; tolerate it.
    local line
    line=$(printf "%s" "$BATTERYSTATS_RAW" | grep -E "Uid ${short_uid}:" | head -1 || true)
    [ -z "$line" ] && return 0
    printf "%s" "$line" | awk -F: '{print $NF}' | awk '{print $1}'
}

ensure_csv() {
    mkdir -p "$OUT_DIR"
    if [ ! -f "$CSV_FILE" ]; then
        echo "$CSV_HEADER" > "$CSV_FILE"
    fi
}

ts_iso() {
    # macOS `date` does not support -Iseconds; use a portable format.
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

ts_filename() {
    date -u +"%Y%m%dT%H%M%SZ"
}

append_csv_row() {
    local row="$1"
    ensure_csv
    printf "%s\n" "$row" >> "$CSV_FILE"
}

write_raw_dump() {
    local path="$1"
    {
        echo "--- section: meta ---"
        echo "label=$LABEL"
        echo "scenario=$SCENARIO"
        echo "duration_s=$DURATION"
        echo "package=$PACKAGE"
        echo "pid=$PID"
        echo "uid_num=$UID_NUM short_uid=$SHORT_UID"
        echo "clk_tck=$(get_clk_tck)"
        echo "battery_level=$(adb shell dumpsys battery | awk -F': ' '/^[[:space:]]*level:/ {gsub(/ /,"",$2); print $2; exit}')"
        echo "--- section: batterystats ---"
        printf "%s\n" "$BATTERYSTATS_RAW"
        echo "--- section: gfxinfo ---"
        printf "%s\n" "$GFXINFO_RAW"
        echo "--- section: top ---"
        cat "$TOP_LOG" 2>/dev/null || true
    } > "$path"
}

log "label=$LABEL scenario=$SCENARIO duration=${DURATION}s package=$PACKAGE"

require_cmd adb
require_cmd awk
require_cmd grep
assert_single_device

if [ "$SKIP_CHECKS" -eq 0 ]; then
    assert_foreground
    assert_battery_level
fi

log "preflight ok"

KEYEVENT_PIDS=()
DISPLAY_ID=""

keyevent() {
    # Background, fire-and-forget. `adb shell input keyevent` is ~50ms per call;
    # backgrounding lets the inter-event interval drive the pace instead of adb latency.
    # `input -d N` targets a specific display; required on devices where the app is on
    # a non-default display (e.g. dock/external screen, virtual display).
    if [ -n "$DISPLAY_ID" ]; then
        adb shell "input -d $DISPLAY_ID keyevent $1" >/dev/null 2>&1 &
    else
        adb shell "input keyevent $1" >/dev/null 2>&1 &
    fi
    KEYEVENT_PIDS+=($!)
}

drain_keyevents() {
    # Give in-flight keyevents a short grace, then SIGKILL the rest so scenario
    # duration stays bounded even on slow (wireless) adb.
    local grace_end=$(( $(date +%s) + 1 ))
    while [ "$(date +%s)" -lt "$grace_end" ]; do
        local any_alive=0
        for pid in ${KEYEVENT_PIDS[@]+"${KEYEVENT_PIDS[@]}"}; do
            if kill -0 "$pid" 2>/dev/null; then any_alive=1; break; fi
        done
        if [ "$any_alive" -eq 0 ]; then break; fi
        sleep 0.1
    done
    # SIGKILL leftovers; suppress bash's "Killed" notifications from the
    # subshell where we reap them.
    {
        for pid in ${KEYEVENT_PIDS[@]+"${KEYEVENT_PIDS[@]}"}; do
            kill -KILL "$pid" 2>/dev/null || true
        done
        wait 2>/dev/null || true
    } 2>/dev/null
    KEYEVENT_PIDS=()
}

run_scenario() {
    local end
    end=$(( $(date +%s) + DURATION ))
    case "$SCENARIO" in
        idle-tab)
            sleep "$DURATION"
            ;;
        button-spam)
            while [ "$(date +%s)" -lt "$end" ]; do
                keyevent KEYCODE_BUTTON_X
                sleep 0.2
            done
            drain_keyevents
            ;;
        tab-cycle)
            local toggle=0
            while [ "$(date +%s)" -lt "$end" ]; do
                if [ $((toggle % 2)) -eq 0 ]; then
                    keyevent KEYCODE_BUTTON_R1
                else
                    keyevent KEYCODE_BUTTON_L1
                fi
                toggle=$((toggle + 1))
                sleep 1
            done
            drain_keyevents
            ;;
        scroll-spam)
            while [ "$(date +%s)" -lt "$end" ]; do
                keyevent KEYCODE_DPAD_DOWN
                sleep 0.1
            done
            drain_keyevents
            ;;
        nav-paced)
            # D-pad RIGHT every 400ms: an actual browsing pace, not a stress test. The
            # 100ms scroll-spam scenario is fast enough that presses queue behind each
            # other, which measures the input backlog rather than the cost of a move.
            while [ "$(date +%s)" -lt "$end" ]; do
                keyevent KEYCODE_DPAD_RIGHT
                sleep 0.4
            done
            drain_keyevents
            ;;
    esac
}

cleanup() {
    if [ -n "${TOP_PID:-}" ]; then
        kill "$TOP_PID" 2>/dev/null || true
    fi
    if [ -n "${TOP_LOG:-}" ] && [ -f "$TOP_LOG" ]; then
        rm -f "$TOP_LOG"
    fi
    restore_plug
}
trap cleanup EXIT INT TERM

fake_unplug
if [ "$PLUGGED_AT_START" -eq 1 ]; then
    sleep 0.5
fi
reset_counters

PID=$(get_pid)
[ -z "$PID" ] && { log_err "could not resolve pid for $PACKAGE"; exit 4; }
UID_NUM=$(get_uid_num)
SHORT_UID=$(format_short_uid "$UID_NUM")
log "pid=$PID uid_num=$UID_NUM short_uid=$SHORT_UID"

DISPLAY_ID=$(get_app_display_id || true)
if [ -n "$DISPLAY_ID" ]; then
    log "app is on display $DISPLAY_ID; routing keyevents there"
else
    log "no display detected for $PACKAGE; using default display"
fi

START_RAW=$(read_proc_stat_jiffies "$PID")
START_UTIME=$(echo "$START_RAW" | awk '{print $1}')
START_STIME=$(echo "$START_RAW" | awk '{print $2}')

start_top_sampler "$PID"
TS_START=$(ts_iso)
log "running scenario: $SCENARIO for ${DURATION}s"
run_scenario
log "scenario complete; collecting metrics"
stop_top_sampler

END_RAW=$(read_proc_stat_jiffies "$PID")
END_UTIME=$(echo "$END_RAW" | awk '{print $1}')
END_STIME=$(echo "$END_RAW" | awk '{print $2}')

collect_gfxinfo
collect_batterystats

CPU_PCT_TOP=$(parse_top_avg_cpu "$PID")
TICK=$(get_clk_tck)
CPU_S_PROC=$(awk -v du="$END_UTIME" -v dus="$START_UTIME" -v ds="$END_STIME" -v dss="$START_STIME" -v tck="$TICK" \
    'BEGIN { printf "%.2f", ((du - dus) + (ds - dss)) / tck }')
TOTAL_FRAMES=$(parse_gfx_total_frames)
JANKY_FRAMES=$(parse_gfx_janky_frames)
JANK_PCT=""
if [ -n "$TOTAL_FRAMES" ] && [ -n "$JANKY_FRAMES" ] && [ "$TOTAL_FRAMES" -gt 0 ] 2>/dev/null; then
    JANK_PCT=$(awk -v j="$JANKY_FRAMES" -v t="$TOTAL_FRAMES" 'BEGIN { printf "%.2f", (j / t) * 100 }')
fi
P50=$(parse_gfx_percentile 50th)
P90=$(parse_gfx_percentile 90th)
P95=$(parse_gfx_percentile 95th)
P99=$(parse_gfx_percentile 99th)
MAH=$(parse_mah_for_uid "$SHORT_UID")

NOTES=""
if [ -z "$TOTAL_FRAMES" ] || [ "${TOTAL_FRAMES:-0}" = "0" ]; then
    NOTES="no_frames"
fi
if [ -z "$MAH" ]; then
    if [ -n "$NOTES" ]; then NOTES="${NOTES};no_mah"; else NOTES="no_mah"; fi
fi

ROW="${TS_START},${LABEL},${SCENARIO},${DURATION},${MAH},${CPU_PCT_TOP},${CPU_S_PROC},${TOTAL_FRAMES},${JANKY_FRAMES},${JANK_PCT},${P50},${P90},${P95},${P99},${NOTES}"
append_csv_row "$ROW"

DUMP_PATH="${OUT_DIR}/$(ts_filename)-${LABEL}-${SCENARIO}.txt"
write_raw_dump "$DUMP_PATH"

log "wrote: $CSV_FILE"
log "wrote: $DUMP_PATH"
log "row:   $ROW"

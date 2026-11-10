#!/usr/bin/env bash
# perfprobe-startup.sh — capture app cold/warm start CPU + frame + ActivityManager metrics.
# Companion to perfprobe.sh for steady-state scenarios. See README in this folder.
set -euo pipefail

PACKAGE_DEFAULT="com.twinlore.app"
ACTIVITY_DEFAULT=".MainActivity"
DURATION_DEFAULT=15
OUT_DIR="perf-runs"
CSV_FILE="${OUT_DIR}/startup-runs.csv"
CSV_HEADER="timestamp,label,mode,duration_s,am_total_time_ms,am_wait_time_ms,launch_state,cpu_s_proc,cpu_pct_top,total_frames,janky_frames,jank_pct,p50_ms,p90_ms,p95_ms,p99_ms,startup_perf_count,notes"

LABEL=""
MODE=""
DURATION="$DURATION_DEFAULT"
PACKAGE="$PACKAGE_DEFAULT"
ACTIVITY="$ACTIVITY_DEFAULT"
DROP_CACHE=0
SKIP_CHECKS=0

usage() {
    cat >&2 <<EOF
Usage: $0 --label NAME --mode {cold|warm} [--duration SECONDS] [--package PKG] [--activity .Class] [--drop-cache] [--skip-checks]

Modes:
  cold        \`am force-stop\` first, then launch. Measures the "I just quit and reopened" experience.
  warm        Launch without force-stop. App may still be in recents.

Options:
  --label NAME         Identifier for this run (e.g. baseline, fix-hilt)
  --duration SECONDS   How long to sample after launch (default: ${DURATION_DEFAULT}). Should be long enough to cover settle.
  --package PKG        Package id (default: ${PACKAGE_DEFAULT})
  --activity .Class    Launch component (default: ${ACTIVITY_DEFAULT})
  --drop-cache         Also drop OS page cache via 'su' before cold launch. Errors if device is not rooted.
  --skip-checks        Skip battery-level preflight (unplug check is mandatory).

Output:
  ${CSV_FILE}                                  — append-only CSV row per run
  ${OUT_DIR}/<ts>-<label>-startup-<mode>.txt   — raw am-output + STARTUP_PERF + per-second top + gfxinfo
EOF
    exit 2
}

require_value() {
    [ $# -ge 2 ] || { echo "Missing value for $1" >&2; usage; }
}

while [ $# -gt 0 ]; do
    case "$1" in
        --label) require_value "$@"; LABEL="$2"; shift 2 ;;
        --mode) require_value "$@"; MODE="$2"; shift 2 ;;
        --duration) require_value "$@"; DURATION="$2"; shift 2 ;;
        --package) require_value "$@"; PACKAGE="$2"; shift 2 ;;
        --activity) require_value "$@"; ACTIVITY="$2"; shift 2 ;;
        --drop-cache) DROP_CACHE=1; shift ;;
        --skip-checks) SKIP_CHECKS=1; shift ;;
        -h|--help) usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

[ -z "$LABEL" ] && { echo "Missing --label" >&2; usage; }
[ -z "$MODE" ] && { echo "Missing --mode" >&2; usage; }
case "$MODE" in cold|warm) ;; *) echo "Unknown mode: $MODE" >&2; usage ;; esac
case "$LABEL" in *,*|*\"*) echo "--label cannot contain commas or quotes" >&2; exit 2 ;; esac
case "$DURATION" in ''|*[!0-9]*) echo "--duration must be a positive integer" >&2; exit 2 ;; esac
[ "$DURATION" -lt 3 ] && { echo "--duration must be >= 3" >&2; exit 2; }

log()    { printf "[startup] %s\n" "$*" >&2; }
log_err(){ printf "[startup][ERROR] %s\n" "$*" >&2; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || { log_err "$1 not on PATH"; exit 3; }
}
require_cmd adb
require_cmd awk
require_cmd grep

assert_single_device() {
    local count
    count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
    if [ "$count" -ne 1 ]; then
        log_err "expected exactly one connected adb device, found $count"
        exit 3
    fi
}
assert_single_device

assert_battery_level() {
    local level
    level=$(adb shell dumpsys battery | awk -F': ' '/^[[:space:]]*level:/ {gsub(/ /,"",$2); print $2; exit}')
    [ -z "$level" ] && { log_err "could not read battery level"; exit 3; }
    if [ "$level" -lt 30 ]; then
        log_err "battery is ${level}% (< 30%) — results will be skewed; pass --skip-checks to override"
        exit 3
    fi
    log "battery level: ${level}%"
}
if [ "$SKIP_CHECKS" -eq 0 ]; then
    assert_battery_level
fi

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
        log "restoring battery state"
        adb shell dumpsys battery reset >/dev/null || true
    fi
}

TOP_LOG=""
TOP_PID=""
LOGCAT_LOG=""
cleanup() {
    if [ -n "${TOP_PID:-}" ]; then kill "$TOP_PID" 2>/dev/null || true; fi
    if [ -n "${TOP_LOG:-}" ] && [ -f "$TOP_LOG" ]; then rm -f "$TOP_LOG"; fi
    if [ -n "${LOGCAT_LOG:-}" ] && [ -f "$LOGCAT_LOG" ]; then rm -f "$LOGCAT_LOG"; fi
    restore_plug
}
trap cleanup EXIT INT TERM

fake_unplug
if [ "$PLUGGED_AT_START" -eq 1 ]; then sleep 0.5; fi

# Cold-mode preparation
if [ "$MODE" = "cold" ]; then
    log "force-stopping $PACKAGE"
    adb shell "am force-stop $PACKAGE" >/dev/null
    sleep 0.5
    if [ "$DROP_CACHE" -eq 1 ]; then
        log "dropping OS page cache via su"
        # su exit non-zero if device not rooted — fail loudly
        if ! adb shell "su -c 'sync; echo 3 > /proc/sys/vm/drop_caches'" >/dev/null 2>&1; then
            log_err "--drop-cache requires a rooted device. Aborting."
            exit 4
        fi
        sleep 0.5
    fi
fi

# Clear logcat so STARTUP_PERF capture is scoped to this launch
adb logcat -c >/dev/null 2>&1 || true

# Reset gfxinfo for any pre-existing process (warm mode); harmless if cold
adb shell "dumpsys gfxinfo $PACKAGE reset" >/dev/null 2>&1 || true

# Launch with -W to get TotalTime/WaitTime/LaunchState
log "launching $PACKAGE/$ACTIVITY in $MODE mode"
TS_START=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
AM_OUTPUT_FILE=$(mktemp -t perfprobe-startup-am.XXXXXX)
adb shell "am start -W -n $PACKAGE/$ACTIVITY" > "$AM_OUTPUT_FILE" 2>&1 &
AM_PID=$!

# Resolve PID (process is starting up — retry briefly).
# pidof exits 1 when no match — guard the pipeline with `|| true` so set -euo pipefail
# doesn't kill the script before the app's process appears.
PID=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    PID=$( (adb shell pidof "$PACKAGE" 2>/dev/null || true) | awk '{print $1}' | tr -d '\r')
    [ -n "$PID" ] && break
    sleep 0.1
done

# Wait for am -W to finish (it returns after the first frame is drawn or short timeout)
wait "$AM_PID" 2>/dev/null || true

if [ -z "$PID" ]; then
    log_err "could not resolve pid for $PACKAGE after launch"
    cat "$AM_OUTPUT_FILE" >&2
    rm -f "$AM_OUTPUT_FILE"
    exit 4
fi
log "pid=$PID"

# Start sampling top for the duration
TOP_LOG=$(mktemp -t perfprobe-startup-top.XXXXXX)
adb shell "top -b -p $PID -n $DURATION -d 1" > "$TOP_LOG" 2>/dev/null &
TOP_PID=$!

log "sampling for ${DURATION}s..."
wait "$TOP_PID" 2>/dev/null || true

# Dump STARTUP_PERF markers from current logcat buffer (tag-filtered).
# We cleared the buffer right before launch, so this captures only this launch.
LOGCAT_LOG=$(mktemp -t perfprobe-startup-logcat.XXXXXX)
adb shell "logcat -d -s STARTUP_PERF" 2>/dev/null > "$LOGCAT_LOG" || true

# Read total CPU consumed by the process since spawn from /proc/$PID/stat.
# This avoids the "top started after the heavy phase" problem — utime+stime
# are cumulative since process creation.
CLK_TCK=$(adb shell "getconf CLK_TCK" 2>/dev/null | tr -d '\r')
[ -z "$CLK_TCK" ] && CLK_TCK=100
PROC_STAT=$(adb shell "cat /proc/$PID/stat" 2>/dev/null || true)
CPU_S_PROC=""
if [ -n "$PROC_STAT" ]; then
    CPU_S_PROC=$(printf "%s" "$PROC_STAT" | awk -v tck="$CLK_TCK" '{
        line = $0
        close_idx = index(line, ") ")
        tail = substr(line, close_idx + 2)
        n = split(tail, parts, " ")
        utime = parts[12] + 0
        stime = parts[13] + 0
        printf "%.2f", (utime + stime) / tck
    }')
fi

# Collect gfxinfo
GFXINFO_RAW=$(adb shell "dumpsys gfxinfo $PACKAGE framestats" 2>/dev/null || true)

# Parse am -W
AM_OUTPUT=$(cat "$AM_OUTPUT_FILE")
TOTAL_TIME_MS=$(printf "%s" "$AM_OUTPUT" | awk -F': *' '/^TotalTime:/ {print $2; exit}' | tr -d '\r')
WAIT_TIME_MS=$(printf "%s" "$AM_OUTPUT" | awk -F': *' '/^WaitTime:/ {print $2; exit}' | tr -d '\r')
LAUNCH_STATE=$(printf "%s" "$AM_OUTPUT" | awk -F': *' '/^LaunchState:/ {print $2; exit}' | tr -d '\r')

# Parse top average CPU
CPU_PCT_TOP=$(awk -v pid="$PID" '
    $1 == pid {
        v = $9
        gsub(/[^0-9.]/, "", v)
        if (v != "") { s += v; n++ }
    }
    END { if (n > 0) printf "%.2f", s / n; else printf "" }
' "$TOP_LOG")

# Parse gfxinfo
TOTAL_FRAMES=$(printf "%s" "$GFXINFO_RAW" | awk '/Total frames rendered:/ {print $4; exit}')
JANKY_FRAMES=$(printf "%s" "$GFXINFO_RAW" | awk '/Janky frames:/ {print $3; exit}')
P50=$(printf "%s" "$GFXINFO_RAW" | awk '/50th percentile:/ {v=$3; gsub(/ms/,"",v); print v; exit}')
P90=$(printf "%s" "$GFXINFO_RAW" | awk '/90th percentile:/ {v=$3; gsub(/ms/,"",v); print v; exit}')
P95=$(printf "%s" "$GFXINFO_RAW" | awk '/95th percentile:/ {v=$3; gsub(/ms/,"",v); print v; exit}')
P99=$(printf "%s" "$GFXINFO_RAW" | awk '/99th percentile:/ {v=$3; gsub(/ms/,"",v); print v; exit}')

JANK_PCT=""
if [ -n "$TOTAL_FRAMES" ] && [ -n "$JANKY_FRAMES" ] && [ "$TOTAL_FRAMES" -gt 0 ] 2>/dev/null; then
    JANK_PCT=$(awk -v j="$JANKY_FRAMES" -v t="$TOTAL_FRAMES" 'BEGIN { printf "%.2f", (j / t) * 100 }')
fi

STARTUP_PERF_COUNT=$(awk 'END {print NR}' "$LOGCAT_LOG")

NOTES=""
[ -z "$TOTAL_TIME_MS" ] && NOTES="no_am_time"
[ -z "$PID" ] && NOTES="${NOTES:+$NOTES;}no_pid"

# Output: CSV row + raw dump
mkdir -p "$OUT_DIR"
if [ ! -f "$CSV_FILE" ]; then
    echo "$CSV_HEADER" > "$CSV_FILE"
fi
ROW="${TS_START},${LABEL},${MODE},${DURATION},${TOTAL_TIME_MS},${WAIT_TIME_MS},${LAUNCH_STATE},${CPU_S_PROC},${CPU_PCT_TOP},${TOTAL_FRAMES},${JANKY_FRAMES},${JANK_PCT},${P50},${P90},${P95},${P99},${STARTUP_PERF_COUNT},${NOTES}"
printf "%s\n" "$ROW" >> "$CSV_FILE"

DUMP_PATH="${OUT_DIR}/$(date -u +"%Y%m%dT%H%M%SZ")-${LABEL}-startup-${MODE}.txt"
{
    echo "--- section: meta ---"
    echo "label=$LABEL"
    echo "mode=$MODE"
    echo "duration_s=$DURATION"
    echo "package=$PACKAGE"
    echo "activity=$ACTIVITY"
    echo "pid=$PID"
    echo "drop_cache=$DROP_CACHE"
    echo "--- section: am_start ---"
    cat "$AM_OUTPUT_FILE"
    echo "--- section: startup_perf_logcat ---"
    cat "$LOGCAT_LOG"
    echo "--- section: top ---"
    cat "$TOP_LOG"
    echo "--- section: gfxinfo ---"
    printf "%s\n" "$GFXINFO_RAW"
} > "$DUMP_PATH"
rm -f "$AM_OUTPUT_FILE"

log "wrote: $CSV_FILE"
log "wrote: $DUMP_PATH"
log "row:   $ROW"

# Print the STARTUP_PERF timeline so the user sees where the launch time went.
if [ -s "$LOGCAT_LOG" ]; then
    log "STARTUP_PERF markers (since process start):"
    awk '/STARTUP_PERF:/ {
        sub(/.*STARTUP_PERF: /, "")
        print "  " $0
    }' "$LOGCAT_LOG" >&2
fi

# Print per-second CPU sparkline for at-a-glance inspection
log "per-second CPU% during sampling window (top started shortly after process spawn):"
awk -v pid="$PID" 'BEGIN { OFS="" } $1 == pid {
    n++
    cpu = $9; gsub(/[^0-9.]/, "", cpu)
    bar = ""
    bars = int(cpu / 5)
    for (i = 0; i < bars; i++) bar = bar "#"
    printf "  s%-3d %5.1f%%  %s\n", n, cpu, bar
}' "$TOP_LOG" >&2

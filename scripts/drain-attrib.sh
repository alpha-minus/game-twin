#!/usr/bin/env bash
# drain-attrib.sh — localize WHICH thread/subsystem inside the app owns a steady
# battery drain, over a long window. Complements perfprobe.sh: where perfprobe
# reports a single whole-process cpu_pct over ~90s (blind to which thread, and
# noisy at low utilization), this samples PER-THREAD CPU jiffies across a long
# window (default 10 min) and ranks threads by CPU consumed — naming the culprit
# (RenderThread / VizCompositor / Chrome_InProc* / a kotlinx DefaultDispatcher
# worker / MediaCodec / the projection FGS thread / …).
#
# Per-thread CPU-time and wakeup counters are charge-state independent, so this
# works over USB. It still fake-unplugs (dumpsys battery unplug) so the
# batterystats component attribution at the end is meaningful.
#
# Put the app in the EXACT state first (e.g. vault → Guide tab showing the offline
# text guide, then leave it still / scroll occasionally per the protocol), THEN run.
#
# Usage: ./scripts/drain-attrib.sh --label NAME [--duration 600] [--package PKG]
set -euo pipefail

PACKAGE="com.twinlore.app"
DURATION=600
LABEL=""
OUT_DIR="perf-runs"

while [ $# -gt 0 ]; do
  case "$1" in
    --label) LABEL="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[ -z "$LABEL" ] && { echo "Missing --label" >&2; exit 2; }
case "$DURATION" in ''|*[!0-9]*) echo "--duration must be integer seconds" >&2; exit 2 ;; esac
mkdir -p "$OUT_DIR"

log() { printf "[drain-attrib] %s\n" "$*" >&2; }

# Target one device. If ANDROID_SERIAL is set, trust it; else require exactly one.
if [ -n "${ANDROID_SERIAL:-}" ]; then
  adb shell true >/dev/null 2>&1 || { echo "ANDROID_SERIAL=$ANDROID_SERIAL not reachable." >&2; exit 3; }
else
  ndev=$(adb devices | grep -cw "device" || true)
  [ "${ndev}" -ne 1 ] && { echo "Set ANDROID_SERIAL (saw ${ndev} devices)." >&2; exit 3; }
fi

PID=$(adb shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r')
[ -z "$PID" ] && { echo "App not running: $PACKAGE" >&2; exit 3; }
log "pid=$PID  duration=${DURATION}s  label=$LABEL"

# Fake-unplug so batterystats attribution is real; reset counters.
was_plugged=$(adb shell dumpsys battery | grep -qE 'AC powered: true|USB powered: true|Wireless powered: true' && echo 1 || echo 0)
restore() { [ "$was_plugged" = 1 ] && adb shell dumpsys battery reset >/dev/null 2>&1 || true; }
trap restore EXIT
[ "$was_plugged" = 1 ] && { log "fake-unplugging (dumpsys battery unplug)"; adb shell dumpsys battery unplug >/dev/null; }
adb shell dumpsys batterystats --reset >/dev/null 2>&1 || true
adb shell "dumpsys gfxinfo $PACKAGE reset" >/dev/null 2>&1 || true

# Per-thread snapshot: "tid|comm|utime+stime(jiffies)". utime/stime are fields
# 14/15 of /proc/tid/stat; comm (field 2) can contain spaces/parens, so parse the
# remainder AFTER the last ')'.
snapshot() {
  adb shell "for d in /proc/$PID/task/*; do tid=\${d##*/}; c=\$(cat \$d/comm 2>/dev/null); s=\$(cat \$d/stat 2>/dev/null); echo \"\$tid|\$c|\$s\"; done" 2>/dev/null | tr -d '\r'
}

log "snapshot A (start) …"
SNAP_A=$(snapshot)
GFX_FRAMES_NOTE=""
log "idling ${DURATION}s — DO NOT plug/unplug; follow the protocol state (still, or occasional scroll) …"
sleep "$DURATION"
log "snapshot B (end) …"
SNAP_B=$(snapshot)

# gfxinfo frame count over the window: nonzero-and-climbing = continuous
# compositor/WebView repaint (animation/repaint leak), zero = nothing rendering.
TOTAL_FRAMES=$(adb shell "dumpsys gfxinfo $PACKAGE" 2>/dev/null | awk -F': ' '/Total frames rendered/ {gsub(/ /,"",$2); print $2; exit}' | tr -d '\r')
[ -z "$TOTAL_FRAMES" ] && TOTAL_FRAMES="?"

OUT="$OUT_DIR/drain-attrib-${LABEL}.txt"
{
  echo "# drain-attrib  label=$LABEL  pid=$PID  duration=${DURATION}s  pkg=$PACKAGE"
  echo "# USER_HZ assumed 100. cpu_pct = per-core average over the window."
  echo "# Total frames rendered during window: $TOTAL_FRAMES  (>0 climbing => continuous repaint)"
  echo
  echo "## Top threads by CPU consumed over the window"
  printf "%-8s %-22s %12s %9s\n" "tid" "thread" "delta_jiffies" "cpu_pct"
  awk -v dur="$DURATION" '
    function key(tid){return tid}
    FNR==NR {
      n=split($0,a,"|"); if(n<3) next; tid=a[1]; comm=a[2]; stat=a[3];
      r=stat; sub(/^.*\) /,"",r); m=split(r,f," "); j=f[12]+f[13];
      sa[tid]=j; ca[tid]=comm; next
    }
    {
      n=split($0,a,"|"); if(n<3) next; tid=a[1]; comm=a[2]; stat=a[3];
      r=stat; sub(/^.*\) /,"",r); m=split(r,f," "); j=f[12]+f[13];
      d=j-(tid in sa ? sa[tid]:0); if(d<0)d=0;
      if(d>0){ printf "%s\t%s\t%d\n", tid, comm, d }
    }
  ' <(printf "%s\n" "$SNAP_A") <(printf "%s\n" "$SNAP_B") \
  | sort -t$'\t' -k3 -nr \
  | awk -v dur="$DURATION" -F'\t' '{ pct=$3/dur; printf "%-8s %-22s %12d %8.2f%%\n", $1, $2, $3, pct }'
  echo
  echo "## batterystats (uid component attribution + wakeups)"
  adb shell "dumpsys batterystats --charged $PACKAGE" 2>/dev/null | tr -d '\r' \
    | grep -iE "Estimated power|Uid .*$PACKAGE|cpu=|fg=|Wake lock|Wakeup|Sensor|proc=|Foreground services" | head -40
} | tee "$OUT"

log "written: $OUT"

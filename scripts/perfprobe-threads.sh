#!/usr/bin/env bash
# perfprobe-threads.sh — per-thread CPU diff across a driven scenario.
#
# perfprobe.sh answers "how much did this cost"; this answers "which thread paid".
# The distinction matters because a Compose recomposition storm and a per-frame GPU
# effect cost about the same process-wide but land on completely different threads,
# and only one of them is visible in /proc at all.
#
# usage: perfprobe-threads.sh <label> <duration_s> <keycode|IDLE> <interval_s> [display]
set -uo pipefail

ADB_FALLBACK="/opt/homebrew/share/android-commandlinetools/platform-tools"
command -v adb >/dev/null 2>&1 || export PATH="$PATH:$ADB_FALLBACK"
command -v adb >/dev/null 2>&1 || { echo "adb not found on PATH or at $ADB_FALLBACK" >&2; exit 3; }

PKG="${PERFPROBE_PACKAGE:-com.twinlore.app}"
LABEL="${1:?usage: perfprobe-threads.sh <label> <duration_s> <keycode|IDLE> <interval_s> [display]}"
DUR="${2:?missing duration}"; KEY="${3:?missing keycode or IDLE}"; IVL="${4:-0.4}"; DISP="${5:-0}"

PID=$(adb shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')
[ -z "$PID" ] && { echo "[perfprobe-threads] $PKG is not running" >&2; exit 4; }

A=$(mktemp -t perfprobe-threads.XXXXXX)
B=$(mktemp -t perfprobe-threads.XXXXXX)
trap 'rm -f "$A" "$B"' EXIT INT TERM

# Key each row by tid so a thread that exits mid-run cannot be confused with one that
# started; comm alone is not unique (there are four DefaultDispatch threads).
snap() {
    adb shell "cd /proc/$PID/task && for x in *; do read -r n < \$x/comm; \
        awk -v n=\"\$n\" -v x=\$x '{t=substr(\$0,index(\$0,\") \")+2); split(t,p,\" \"); \
        print x\"|\"n, p[12]+p[13]}' \$x/stat; done" 2>/dev/null | tr -d '\r'
}

adb shell "dumpsys gfxinfo $PKG reset" >/dev/null 2>&1
snap > "$A"
T0=$(date +%s); END=$(( T0 + DUR ))
if [ "$KEY" != "IDLE" ]; then
    while [ "$(date +%s)" -lt "$END" ]; do
        adb shell "input -d $DISP keyevent $KEY" >/dev/null 2>&1 &
        sleep "$IVL"
    done
    wait 2>/dev/null
else
    sleep "$DUR"
fi
T1=$(date +%s)
snap > "$B"

TICK=$(adb shell getconf CLK_TCK | tr -d '\r'); TICK=${TICK:-100}
FRAMES=$(adb shell "dumpsys gfxinfo $PKG" 2>/dev/null | awk '/Total frames rendered:/ {print $4; exit}')
FRAMES=${FRAMES:-0}
ELAPSED=$(( T1 - T0 )); [ "$ELAPSED" -lt 1 ] && ELAPSED=1

echo "=== $LABEL  ${ELAPSED}s  frames=$FRAMES ==="
awk -v t="$TICK" -v d="$ELAPSED" '
    NR==FNR { a[$1]=$2; next }
    { delta = $2 - (($1 in a) ? a[$1] : 0)
      if (delta > 0) { split($1,k,"|"); printf "%-22s %6.2fs  %5.1f%% of a core\n", k[2], delta/t, 100*(delta/t)/d } }
' "$A" "$B" | sort -k2 -rn | head -12
awk -v t="$TICK" -v d="$ELAPSED" -v f="$FRAMES" '
    NR==FNR { a[$1]=$2; next }
    { delta = $2 - (($1 in a) ? a[$1] : 0); if (delta > 0) s += delta }
    END { printf "%-22s %6.2fs  %5.1f%% of a core", "TOTAL", s/t, 100*(s/t)/d
          if (f > 0) printf "  (%.1f ms CPU per frame)", 1000*(s/t)/f
          printf "\n" }
' "$A" "$B"

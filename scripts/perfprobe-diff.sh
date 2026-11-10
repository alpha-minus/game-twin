#!/usr/bin/env bash
# perfprobe-diff.sh — compare two labels across all scenarios from perf-runs.csv
set -euo pipefail

CSV_FILE="perf-runs/perf-runs.csv"

usage() {
    cat >&2 <<EOF
Usage: $0 LABEL_A LABEL_B

Reads $CSV_FILE and prints, for each scenario where both labels have a row,
a side-by-side table with absolute and percent delta on mAh, cpu_pct_top,
jank_pct, and p95_ms. The most recent row per (label, scenario) is used.
EOF
    exit 2
}

[ $# -eq 2 ] || usage
LABEL_A="$1"
LABEL_B="$2"

[ -f "$CSV_FILE" ] || { echo "Missing $CSV_FILE" >&2; exit 3; }

awk -F, -v la="$LABEL_A" -v lb="$LABEL_B" '
    NR == 1 {
        for (i = 1; i <= NF; i++) col[$i] = i
        next
    }
    {
        label = $col["label"]
        scenario = $col["scenario"]
        if (label != la && label != lb) next
        # Keep latest by row order (CSV is append-only chronological).
        key = label SUBSEP scenario
        for (i = 1; i <= NF; i++) data[key, i] = $i
        seen[scenario] = 1
        if (label == la) has_a[scenario] = 1
        if (label == lb) has_b[scenario] = 1
    }
    END {
        keys = "mAh cpu_pct_top jank_pct p95_ms"
        n = split(keys, fields, " ")

        printed = 0
        for (s in seen) {
            if (!(has_a[s] && has_b[s])) continue
            printed = 1
            printf "scenario: %s\n", s
            printf "%-22s %-22s %-22s %s\n", "", la, lb, "delta"
            for (i = 1; i <= n; i++) {
                f = fields[i]
                a = data[la, s, col[f]]
                b = data[lb, s, col[f]]
                if (a == "" || b == "") {
                    printf "%-22s %-22s %-22s %s\n", f, (a == "" ? "-" : a), (b == "" ? "-" : b), "n/a"
                    continue
                }
                diff = b - a
                pct = (a + 0 == 0) ? "n/a" : sprintf("%+.1f%%", (diff / a) * 100)
                printf "%-22s %-22s %-22s %s\n", f, a, b, pct
            }
            print ""
        }
        if (!printed) {
            print "No scenarios have rows for both labels in " FILENAME > "/dev/stderr"
            exit 4
        }
    }
' "$CSV_FILE"

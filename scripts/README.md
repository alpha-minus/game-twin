# Performance profiling

Host-side bash + `adb` harnesses for measuring app CPU, frame timing, and
energy use. Built to answer two questions:

1. **"Did my optimization help?"** — run the same scenario before and after a
   code change, compare the CSV row.
2. **"Where's the CPU going during startup?"** — capture a cold or warm
   launch with per-second CPU samples and the timeline of the existing
   `STARTUP_PERF` PerfLog markers.

All scripts live in this folder, append to `perf-runs/` (gitignored), and
exit non-zero on preflight failure with an actionable message.

## TL;DR — what to run

| Question | Script | Example |
|---|---|---|
| Did this code change help? | `perfprobe.sh` + `perfprobe-diff.sh` | `./scripts/perfprobe.sh --label baseline --scenario button-spam --duration 30` |
| Is cold start slow? | `perfprobe-startup.sh --mode cold` | `./scripts/perfprobe-startup.sh --label cold-1 --mode cold --duration 12` |
| Is warm start (Recents → app) janky? | `perfprobe-startup.sh --mode warm` | `./scripts/perfprobe-startup.sh --label warm-1 --mode warm --duration 8` |
| Compare two runs | `perfprobe-diff.sh` | `./scripts/perfprobe-diff.sh baseline fix1` |

Always run from the repo root. Output goes to `perf-runs/`.

## Prerequisites

- `adb` on `PATH` (Android Platform Tools)
- Exactly one device connected and authorised (`adb devices` shows one
  `device` line — disconnect or use `adb disconnect <serial>` for extras)
- App installed; if you uninstalled to switch keystores, re-grant the SAF
  home-folder permission before measuring
- Battery ≥ 30 % (warns and refuses below; bypass with `--skip-checks` if
  you know what you're doing)
- Device unplugged from power — the scripts fake-unplug via
  `dumpsys battery unplug` while the run is in flight and restore on exit
  via a trap; safe to leave the cable in

If `adb devices` shows nothing on a wireless device, the wireless daemon's
port has cycled. On the device: **Developer options → Wireless debugging →
Pair / IP and port**, then `adb connect 192.168.X.X:<new-port>`.

## `perfprobe.sh` — steady-state scenarios

Drives a fixed input pattern at the device while sampling. Use this to
isolate "is this specific UI interaction expensive?"

```bash
./scripts/perfprobe.sh \
    --label baseline \
    --scenario {idle-tab | button-spam | tab-cycle | scroll-spam} \
    [--duration 30] \
    [--package com.twinlore.app] \
    [--skip-checks]
```

| Scenario | What it does | Tests |
|---|---|---|
| `idle-tab` | Sits there for `--duration` seconds. No input. | Background recompositions, idle CPU, animation leaks |
| `button-spam` | KEYCODE_BUTTON_X every 200 ms | `controllerAction` StateFlow fanout |
| `tab-cycle` | L1/R1 alternating every 1 s | Tab activation cost |
| `scroll-spam` | DPAD_DOWN every 100 ms | Scroll-driven recomposition, list snapshotFlow churn |
| `nav-paced` | D-pad RIGHT every 400 ms. A realistic browsing pace — tests the per-move cost rather than the input backlog. | Navigation cost at an actual browsing pace |

Open the app to the screen you want to measure first. On multi-display
devices the script auto-detects which display has the app and routes
keyevents there.

## `perfprobe-threads.sh` — which thread paid

`perfprobe.sh` answers "how much did this cost"; `perfprobe-threads.sh`
answers "which thread paid". That distinction matters because a Compose
recomposition storm and a per-frame GPU effect can cost about the same
process-wide but land on completely different threads.

It snapshots per-thread CPU from `/proc/<pid>/task/*/stat` before and after
driving (or idling through) a scenario, then diffs the two snapshots keyed
by tid, printing a per-thread table sorted by CPU cost plus a TOTAL line
with ms-of-CPU-per-frame.

```bash
./scripts/perfprobe-threads.sh <label> <duration_s> <keycode|IDLE> <interval_s> [display]
```

Example — idle baseline on the library screen:

```bash
./scripts/perfprobe-threads.sh library-idle 10 IDLE 0
```

Pass a real keycode (e.g. `KEYCODE_DPAD_RIGHT`) instead of `IDLE` to drive
input at the given interval while sampling, and an optional trailing
`display` argument to target a non-default display.

## `perfprobe-startup.sh` — cold and warm launch

Force-stops (cold mode) or just `am start`s the activity, then samples for
`--duration` seconds. Reads the existing `STARTUP_PERF` PerfLog markers so
you get a timeline of where startup time went, plus per-second CPU.

```bash
./scripts/perfprobe-startup.sh \
    --label cold-baseline \
    --mode {cold | warm} \
    [--duration 12] \
    [--package com.twinlore.app] \
    [--activity .MainActivity] \
    [--drop-cache]
```

- `--mode cold` runs `am force-stop` before launching. Closest you can
  get without a reboot to "I just quit and reopened the app."
- `--mode warm` skips the force-stop; uses the live process if any. Use
  when measuring foreground/background transitions.
- `--drop-cache` additionally drops the OS page cache via `su` (rooted
  devices only — errors out otherwise). True post-boot cold start.

At end-of-run the script prints:

```
[startup] STARTUP_PERF markers (since process start):
  +524ms Application.onCreate begin
  +683ms Application.onCreate end
  +723ms MainActivity.onCreate begin id=...
  ...
[startup] per-second CPU% during sampling window:
  s1   188.0%  ######################################
  s2   145.0%  #############################
  ...
```

The marker timeline tells you the **shape** of the launch (what's blocking
the first frame); the per-second CPU tells you **where to look next**.

## `perfprobe-diff.sh` — before / after comparison

Reads `perf-runs/perf-runs.csv` and prints a side-by-side table for two
labels across all scenarios where both labels have a run.

```bash
./scripts/perfprobe-diff.sh baseline fix-controller-gate
```

```
scenario: button-spam
                       baseline               fix-controller-gate    delta
mAh                    12.4                   6.1                    -50.8%
cpu_pct_top            34.2                   14.7                   -57.0%
jank_pct               2.1%                   1.0%                   -52.4%
p95_ms                 24                     18                     -25.0%
```

The startup harness writes to a separate `perf-runs/startup-runs.csv`
(different schema). To compare startup runs by hand:

```bash
awk -F, '$2=="baseline-cold" || $2=="fix-cold"' perf-runs/startup-runs.csv
```

## Reading the output

After every run you get:

```
perf-runs/
├── perf-runs.csv                          # append-only, one row per perfprobe.sh run
├── startup-runs.csv                       # one row per perfprobe-startup.sh run
└── <timestamp>-<label>-<scenario>.txt     # raw dumpsys / top / logcat for forensic review
```

CSV columns (perfprobe.sh): `timestamp,label,scenario,duration_s,mAh,
cpu_pct_top,cpu_s_proc,total_frames,janky_frames,jank_pct,p50_ms,
p90_ms,p95_ms,p99_ms,notes`. Empty cells are real "could not measure"
signals (e.g. `mAh` is empty on devices without per-uid charge stats);
`notes` carries tags like `no_frames` or `no_mah` for those.

The raw `.txt` dump is the place to chase weird numbers — full
`dumpsys batterystats`, `dumpsys gfxinfo framestats`, and the per-second
`top` series are all there under `--- section: <name> ---` headers.

## A typical optimization session

```bash
# 1. Baseline
./scripts/perfprobe.sh --label baseline --scenario button-spam --duration 30
./scripts/perfprobe-startup.sh --label baseline-cold --mode cold --duration 12

# 2. Make a code change. Build, install.
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Re-measure
./scripts/perfprobe.sh --label fix-1 --scenario button-spam --duration 30
./scripts/perfprobe-startup.sh --label fix-1-cold --mode cold --duration 12

# 4. Compare
./scripts/perfprobe-diff.sh baseline fix-1
awk -F, '$2~/^(baseline-cold|fix-1-cold)$/' perf-runs/startup-runs.csv
```

Repeat each measurement 3-5 times and take the median if the numbers are
close to noise. CPU sampling at 1 Hz is coarse; single runs can vary ±10 %.

## Common gotchas

**Signature mismatch on `adb install -r`.** You uninstalled a release-signed
APK and installed the debug build, then tried `install -r` again later.
Either pass `-d` to allow downgrade or `adb uninstall com.twinlore.app`
first. Uninstalling preserves the library data on disk (it lives in your
home folder) but wipes the SAF permission grant — you'll re-pick the home
folder on next launch.

**Carousel artwork doesn't render after a fresh install of the debug
build.** Same SAF root cause: persistable URI permission for the home
folder tree is package-+-signature-scoped and gets wiped on uninstall.
Re-grant via the in-app folder picker.

**`mAh` is always empty in the CSV.** Many devices report "Not supported on
this device" for per-uid charge stats in `dumpsys batterystats`. There's no
workaround — the CPU columns are your main signal.

**Cold-start numbers vary a lot between runs.** Process spawn + DEX + Hilt
graph construction is genuinely noisy on Android. Take 3-5 samples and
median if the deltas are tight.

**Wireless adb drops mid-run.** The wireless daemon's port cycles when the
device sleeps. Re-pair via Developer options. The harness's cleanup trap
(`dumpsys battery reset` on exit) won't run if the connection is fully
dead; reset manually with `adb shell dumpsys battery reset` after
reconnect.

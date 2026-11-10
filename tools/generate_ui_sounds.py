#!/usr/bin/env python3
"""Generate UI feedback sounds for game-twin.

Output: 48 kHz / 16-bit / mono WAV files in app/src/main/res/raw/.
6 files, one per event:
  ui_{focus|switch|confirm|back|refresh|error}.wav

Single "Soft" set: warm, low, mellow motifs (minimal partials, slow attacks,
long releases), plus a "magic discovery shimmer" for refresh (the refresh
action detects the last-played game, so it sounds like finding something
rather than reloading) voiced to match the set's warmth.

All 6 files are peak-normalized to the same level (-6 dBFS) so the
per-event gain table in FeedbackTuning.kt is the only loudness knob.

Replace-by-overwrite contract: to swap in curated sounds later, overwrite
the 6 files (same names, WAV format), keep peaks roughly matched, done.
No code change required.

Stdlib only — no ffmpeg/sox/numpy required. Run: python3 tools/generate_ui_sounds.py
"""
import math
import os
import struct
import wave

SR = 48000
PEAK = 0.5  # -6 dBFS common peak
OUT = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "res", "raw",
))


def envelope(i, n, attack_s, release_s):
    t = i / SR
    dur = n / SR
    a = min(1.0, t / attack_s) if attack_s > 0 else 1.0
    r = min(1.0, max(0.0, (dur - t) / release_s)) if release_s > 0 else 1.0
    return a * r


def synth(dur_s, partials, attack=0.003, release=0.02, sweep=1.0):
    """partials: list of (freq_hz, amplitude).

    sweep scales the f*t phase term linearly over the duration; the instantaneous
    end frequency is f*(2*sweep - 1), NOT f*sweep. Treat it as a subtle pitch-bend
    knob, not an exact end-frequency multiplier.
    """
    n = int(SR * dur_s)
    out = []
    for i in range(n):
        t = i / SR
        k = 1.0 + (sweep - 1.0) * (t / dur_s)
        s = sum(a * math.sin(2 * math.pi * f * k * t) for f, a in partials)
        out.append(s * envelope(i, n, attack, release))
    return out


def normalize(samples, peak=PEAK):
    m = max(abs(s) for s in samples) or 1.0
    return [s / m * peak for s in samples]


def write(name, samples):
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples
        ))
    ms = len(samples) / SR * 1000
    print(f"wrote {path} ({ms:.0f} ms, {os.path.getsize(path)} bytes)")


def notes(seq, gap_s=0.008):
    """seq: list of (dur_s, partials, kwargs-dict) synth segments joined with short gaps."""
    out = []
    for i, (dur, partials, kw) in enumerate(seq):
        if i > 0:
            out.extend([0.0] * int(SR * gap_s))
        out.extend(synth(dur, partials, **kw))
    return out


def layer(events):
    """events: list of (offset_s, samples). Mix into one buffer (sum, no normalization)."""
    total = max(int(SR * off) + len(s) for off, s in events)
    out = [0.0] * total
    for off, s in events:
        base = int(SR * off)
        for i, v in enumerate(s):
            out[base + i] += v
    return out


# ---------------------------------------------------------------------------
# "Soft" set — warm, low, mellow
# Minimal partials: [(f, 1.0), (2*f, 0.12)], slow attacks, long releases
# ---------------------------------------------------------------------------
def soft_partials(f):
    return [(f, 1.0), (2 * f, 0.12)]

# FOCUS: 440 Hz, 40 ms — very soft tick
write("ui_focus.wav", normalize(synth(
    0.040, soft_partials(440), attack=0.010, release=0.028)))

# SWITCH: 392→523 Hz — gentle two-note rise, rhythmically distinct
write("ui_switch.wav", normalize(notes([
    (0.045, soft_partials(392), {"attack": 0.010, "release": 0.032}),
    (0.055, soft_partials(523), {"attack": 0.010, "release": 0.039}),
], gap_s=0.010)))

# CONFIRM: 392→587 Hz — warm ascending two-note
write("ui_confirm.wav", normalize(notes([
    (0.050, soft_partials(392), {"attack": 0.010, "release": 0.035}),
    (0.080, soft_partials(587), {"attack": 0.010, "release": 0.056}),
], gap_s=0.010)))

# BACK: 523→349 Hz — warm descending mirror
write("ui_back.wav", normalize(notes([
    (0.045, soft_partials(523), {"attack": 0.010, "release": 0.032}),
    (0.075, soft_partials(349), {"attack": 0.010, "release": 0.053}),
], gap_s=0.010)))

# REFRESH: "magic discovery shimmer", soft voicing — a warm rising glissando
# from G4 ending in two gentle sparkles an octave lower than the bright
# version, so it sits inside the set's mellow register. Refresh detects the
# last-played game, so it sounds like finding something, not reloading.
write("ui_refresh.wav", normalize(layer([
    # Base: continuous rising glissando sweeping G4 upward
    (0.0, synth(0.140, [(392, 1.0), (784, 0.25)],
                attack=0.012, release=0.060, sweep=1.5)),
    # Twinkle 1 (soft)
    (0.105, [s * 0.55 for s in synth(0.070, [(1046, 1.0), (1568, 0.2)],
                                     attack=0.005, release=0.055)]),
    # Twinkle 2 (soft)
    (0.165, [s * 0.6 for s in synth(0.100, [(1318, 1.0), (1865, 0.25)],
                                    attack=0.005, release=0.080)]),
])))

# ERROR: deep dull double thud
_thud = synth(0.050, [(165, 1.0), (98, 0.6)], attack=0.010, release=0.035)
_gap = [0.0] * int(SR * 0.070)
write("ui_error.wav", normalize(_thud + _gap + _thud))

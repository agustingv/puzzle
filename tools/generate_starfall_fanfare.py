#!/usr/bin/env python3
"""
Starfall's victory fanfare, for the end of the campaign: synthesised in the same chiptune style as the
original game's effects (projects/gnome/vala/game/tools/gen_sounds.py), with numpy and the stdlib
`wave` module. A square-wave lead over a triangle bass, a rising call and a held major chord.

Writes app/src/main/assets/starfall/fanfare.wav. Re-run after tweaking:
    python3 tools/generate_starfall_fanfare.py
"""

import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "starfall" / "fanfare.wav"
SR = 44100

# Tempo: one beat is a crotchet.
BEAT = 0.16


def freq(note):
    """A note name like 'C5' or 'G#4' as its frequency."""
    names = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
    name, octave = note[:-1], int(note[-1])
    return 440.0 * 2 ** ((names[name] + 12 * (octave + 1) - 69) / 12)


def square(f, t, duty=0.5):
    return np.where((f * t) % 1.0 < duty, 1.0, -1.0)


def triangle(f, t):
    return 2.0 * np.abs(2.0 * ((f * t) % 1.0) - 1.0) - 1.0


def envelope(n, attack=0.004, release=0.05, sustain=0.75):
    """A quick attack, a little decay to the sustain level, and a release at the end."""
    e = np.full(n, sustain)
    na = max(1, int(attack * SR))
    nd = min(n, na + int(0.04 * SR))
    e[:na] = np.linspace(0.0, 1.0, na)
    e[na:nd] = np.linspace(1.0, sustain, nd - na)
    nr = min(n, int(release * SR))
    e[n - nr:] *= np.linspace(1.0, 0.0, nr)
    return e


def voice(notes, wave_fn, gain, vibrato=0.0):
    """A line of (note, beats) pairs, None for a rest."""
    parts = []
    for note, beats in notes:
        n = int(beats * BEAT * SR)
        t = np.arange(n) / SR
        if note is None:
            parts.append(np.zeros(n))
            continue
        f = freq(note)
        if vibrato:
            # Only on long notes, after they have settled.
            f = f * (1.0 + vibrato * np.sin(2 * np.pi * 6.0 * t) * np.clip((t - 0.25) / 0.2, 0.0, 1.0))
            phase = np.cumsum(f) / SR
            y = np.where(phase % 1.0 < 0.5, 1.0, -1.0) if wave_fn is square else 2.0 * np.abs(2.0 * (phase % 1.0) - 1.0) - 1.0
        else:
            y = wave_fn(f, t)
        parts.append(y * envelope(n, release=min(0.08, beats * BEAT * 0.3)) * gain)
    return np.concatenate(parts)


# The call: three quick notes up, twice, then a climb to the held top note.
LEAD = [
    ("G4", 1), ("G4", 1), ("G4", 1), ("C5", 3),
    ("A#4", 1), ("A#4", 1), ("A#4", 1), ("D#5", 3),
    ("F5", 1), ("G5", 1), ("A5", 1), ("C6", 9),
]
HARMONY = [
    ("E4", 1), ("E4", 1), ("E4", 1), ("G4", 3),
    ("F4", 1), ("F4", 1), ("F4", 1), ("A#4", 3),
    ("C5", 1), ("D5", 1), ("F5", 1), ("G5", 9),
]
BASS = [
    ("C3", 3), ("C3", 3),
    ("D#3", 3), ("D#3", 3),
    ("F3", 3), ("C3", 9),
]


def fanfare():
    lead = voice(LEAD, square, 0.42, vibrato=0.006)
    harmony = voice(HARMONY, lambda f, t: square(f, t, duty=0.25), 0.22)
    bass = voice(BASS, triangle, 0.55)
    n = max(len(lead), len(harmony), len(bass))
    mix = np.zeros(n)
    for part in (lead, harmony, bass):
        mix[: len(part)] += part
    # A little room: a soft echo a beat and a half behind.
    delay = int(BEAT * 1.5 * SR)
    echo = np.zeros(n + delay)
    echo[:n] += mix
    echo[delay:] += mix * 0.22
    return echo


def save(y, gain=0.8):
    y = np.tanh(y * 1.1)
    y = y / (float(np.max(np.abs(y))) or 1.0) * gain
    fade = int(0.3 * SR)
    y[-fade:] *= np.linspace(1.0, 0.0, fade)
    y[:16] *= np.linspace(0.0, 1.0, 16)
    data = np.clip(y * 32767.0, -32768, 32767).astype("<i2")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    print(f"fanfare: {len(y) / SR:.2f} s -> {OUT}")


if __name__ == "__main__":
    save(fanfare())

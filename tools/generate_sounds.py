"""Generates the game's sound effects, so that every one of them is original and free of licences.

Each sound is synthesised from sine waves and written to res/raw. Run from the project root after
changing one; needs numpy and ffmpeg:

    python3 tools/generate_sounds.py
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

RATE = 44100
OUT = Path("app/src/main/res/raw")

# Pitches in Hz.
C4, EB4, G4, C5, E5, G5, B5, C6 = 261.63, 311.13, 392.00, 523.25, 659.25, 783.99, 987.77, 1046.50
C7, E7, G7 = 2093.00, 2637.02, 3135.96


def times(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def tone(pitch, seconds, harmonics=(1.0,), attack=0.004, decay=6.0):
    """A note: its harmonics, a quick attack, an exponential decay and a fade out to avoid a click."""
    t = times(seconds)
    wave_ = sum(level * np.sin(2 * np.pi * pitch * (n + 1) * t) for n, level in enumerate(harmonics))
    envelope = np.minimum(t / attack, 1.0) * np.exp(-decay * t)
    fade = min(len(t), int(0.01 * RATE))
    envelope[-fade:] *= np.linspace(1, 0, fade)
    return wave_ * envelope


def mix(seconds, *placed):
    """Lays (start, samples, level) notes on one track."""
    track = np.zeros(int(seconds * RATE))
    for start, samples, level in placed:
        first = int(start * RATE)
        end = min(len(track), first + len(samples))
        track[first:end] += samples[: end - first] * level
    return track


def echo(track, delay=0.07, level=0.22, repeats=3):
    """A hint of room, so that the notes do not sound dry. The track must leave room for it."""
    out = track.copy()
    for n in range(1, repeats + 1):
        shift = int(delay * n * RATE)
        out[shift:] += track[:-shift] * level**n
    return out


def normalised(track, peak):
    return track / np.max(np.abs(track)) * peak


BRASS = (1.0, 0.6, 0.42, 0.26, 0.16, 0.08)
BELL = (1.0, 0.0, 0.3, 0.0, 0.12)
SOFT = (1.0, 0.25, 0.08)


def tick():
    return normalised(tone(1400, 0.035, attack=0.001, decay=120), 0.22)


def hit():
    return normalised(
        mix(0.3, (0, tone(E5, 0.14, BELL, decay=16), 0.8), (0.07, tone(B5, 0.23, BELL, decay=14), 1.0)),
        0.45,
    )


def miss():
    return normalised(
        mix(0.3, (0, tone(220, 0.14, SOFT, decay=14), 1.0), (0.09, tone(165, 0.21, SOFT, decay=12), 1.0)),
        0.45,
    )


def lose():
    return normalised(
        echo(
            mix(
                1.1,
                (0, tone(G4, 0.24, SOFT, attack=0.01, decay=6), 1.0),
                (0.2, tone(EB4, 0.24, SOFT, attack=0.01, decay=6), 1.0),
                (0.4, tone(C4, 0.5, SOFT, attack=0.01, decay=5), 1.0),
            ),
        ),
        0.5,
    )


def win():
    """The fanfare: a rising call, a short answer and a held C major chord with bells on top."""

    def brass(pitch, seconds, decay=3.5):
        return tone(pitch, seconds, BRASS, attack=0.015, decay=decay)

    chord_at = 0.72
    return normalised(
        echo(
            mix(
                2.0,
                (0.00, brass(G4, 0.12), 0.8),
                (0.11, brass(C5, 0.12), 0.85),
                (0.22, brass(E5, 0.12), 0.9),
                (0.33, brass(G5, 0.2), 1.0),
                (0.54, brass(E5, 0.09), 0.8),
                (0.63, brass(G5, 0.09), 0.9),
                (chord_at, brass(C6, 1.15, 2.6), 1.0),
                (chord_at, brass(G5, 1.15, 2.6), 0.7),
                (chord_at, brass(E5, 1.15, 2.6), 0.7),
                (chord_at, brass(C5, 1.15, 2.6), 0.8),
                (chord_at + 0.1, tone(C7, 0.5, BELL, decay=9), 0.3),
                (chord_at + 0.2, tone(E7, 0.5, BELL, decay=9), 0.3),
                (chord_at + 0.3, tone(G7, 0.6, BELL, decay=8), 0.3),
            ),
        ),
        0.85,
    )


SOUNDS = {"tick": tick, "hit": hit, "miss": miss, "lose": lose, "win": win}


def write(name, track):
    with tempfile.TemporaryDirectory() as folder:
        source = Path(folder) / "sound.wav"
        with wave.open(str(source), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes((track * 32767).astype(np.int16).tobytes())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-c:a", "libvorbis", "-q:a", "5",
             "-map_metadata", "-1", str(OUT / f"sound_{name}.ogg")],
            check=True,
        )


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in SOUNDS.items():
        write(name, make())

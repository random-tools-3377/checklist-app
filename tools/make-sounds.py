#!/usr/bin/env python3
"""Makes the app's two celebration sounds, drawn for this project, into shared/src/commonMain/composeResources/files/sounds.

celebrate_tick.wav: a short, soft two-note blip for a tick.
celebrate_big.wav: a rising four-note chime for finishing something big or buying a reward.

Plain sine tones with soft starts and fades, 16-bit mono at 22,050 Hz. Run from the repository
root: python3 tools/make-sounds.py
"""
import math
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "shared" / "src" / "commonMain" / "composeResources" / "files" / "sounds"


def note(freq, start, length, volume=0.5):
    """(start in seconds, samples) for one note with a quick attack and a smooth decay."""
    n = int(length * RATE)
    samples = []
    for i in range(n):
        t = i / RATE
        attack = min(1.0, t / 0.008)
        decay = math.exp(-4.0 * t / length)
        tone = math.sin(2 * math.pi * freq * t) + 0.25 * math.sin(4 * math.pi * freq * t)
        samples.append(volume * attack * decay * tone / 1.25)
    return start, samples


def mix(notes, total):
    out = [0.0] * int(total * RATE)
    for start, samples in notes:
        offset = int(start * RATE)
        for i, value in enumerate(samples):
            if offset + i < len(out):
                out[offset + i] += value
    # The last few milliseconds fade to silence, so there is no click at the end.
    fade = int(0.02 * RATE)
    for i in range(fade):
        out[-1 - i] *= i / fade
    return out


def write(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = min(1.0, 0.9 / peak)
    with wave.open(str(OUT / name), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", int(s * scale * 32767)) for s in samples))


if __name__ == "__main__":
    write("celebrate_tick.wav", mix([note(880.0, 0.0, 0.09, 0.45), note(1318.5, 0.06, 0.12, 0.4)], 0.2))
    c5, e5, g5, c6 = 523.25, 659.25, 783.99, 1046.5
    write("celebrate_big.wav", mix([note(c5, 0.0, 0.35), note(e5, 0.12, 0.35), note(g5, 0.24, 0.4), note(c6, 0.36, 0.6)], 1.0))
    print("Wrote", OUT / "celebrate_tick.wav", "and", OUT / "celebrate_big.wav")

"""Render the original Our TV sounds to WAV files for the TV.

Same notes, timings and hall reverb as the browser prototype's Web Audio version,
normalised so they are clearly audible on TV speakers. Usage: py make_sounds.py <out dir>
"""
import sys
import wave
from pathlib import Path

import numpy as np

RATE = 44100
rng = np.random.default_rng(7)


def tone(buf, f, at, dur, gain, kind="sine"):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    wave_ = np.sin(2 * np.pi * f * t) if kind == "sine" else 2 / np.pi * np.arcsin(np.sin(2 * np.pi * f * t))
    attack = min(0.6, dur * 0.3)
    env = np.where(t < attack, 0.0001 * (gain / 0.0001) ** (t / attack), gain * (0.0001 / gain) ** ((t - attack) / (dur - attack)))
    start = int(at * RATE)
    buf[start:start + n] += wave_ * env


def hall(dry, wet=0.42, length=2.6):
    n = int(length * RATE)
    impulse = rng.uniform(-1, 1, n) * (1 - np.arange(n) / n) ** 3.2
    size = 1 << int(np.ceil(np.log2(len(dry) + n)))
    rev = np.fft.irfft(np.fft.rfft(dry, size) * np.fft.rfft(impulse, size), size)[:len(dry) + n]
    rev /= np.max(np.abs(rev)) or 1
    out = np.zeros(len(dry) + n)
    out[:len(dry)] += dry / (np.max(np.abs(dry)) or 1)
    return out + rev * wet


def render(name, seconds, notes, peak):
    buf = np.zeros(int(seconds * RATE))
    for note in notes:
        tone(buf, *note)
    out = hall(buf)
    end = np.max(np.nonzero(np.abs(out) > 1e-4)) + 1
    out = out[:end]
    fade = min(len(out), int(0.05 * RATE))
    out[-fade:] *= np.linspace(1, 0, fade)
    out = out / np.max(np.abs(out)) * peak
    data = (out * 32767).astype("<i2")
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data.tobytes())
    print(name, round(len(data) / RATE, 2), "s")


OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
render("sig", 6.0, [(f, 0, 4.6, 0.09) for f in (55, 82.4, 110, 164.8)]
       + [(f, 1.1 + i * 0.34, 3, 0.05, "tri") for i, f in enumerate((329.6, 493.9, 659.3))]
       + [(739, 2.5, 3.2, 0.05, "tri"), (987.8, 2.9, 2.6, 0.022)], 0.9)
render("focus", 0.4, [(392, 0, 0.32, 0.03), (784, 0, 0.2, 0.008)], 0.4)
render("pick", 1.8, [(146.8, 0, 1.4, 0.09), (293.7, 0, 1.2, 0.05, "tri"), (440, 0.12, 1.2, 0.05, "tri"), (659.3, 0.26, 1.5, 0.05, "tri")], 0.8)
render("open", 1.2, [(196, 0, 1, 0.05), (293.7, 0.08, 1, 0.03)], 0.6)
render("bday", 4.0, [(f, i * 0.32, 1.5, 0.045, "tri") for i, f in enumerate((440, 493.9, 554.4, 659.3, 554.4, 659.3, 739, 880))]
       + [(f, i * 0.95, 1.8, 0.06) for i, f in enumerate((110, 146.8, 164.8))], 0.85)
render("night", 3.4, [(f, i * 0.45, 2.2, 0.05, "tri") for i, f in enumerate((659.3, 493.9, 392))] + [(98, 0, 3, 0.07)], 0.7)

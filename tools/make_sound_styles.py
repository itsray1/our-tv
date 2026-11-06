"""Render four Our TV sound styles for the family to choose from.

Each style has the same six moments as the app (sig, focus, pick, open, bday, night) plus a demo
file that plays them in order with short gaps. Usage: py make_sound_styles.py <out dir>
The birthday tune is "Happy Birthday to You", which is in the public domain.
"""
import sys
import wave
from pathlib import Path

import numpy as np

RATE = 44100
rng = np.random.default_rng(11)

# Note frequencies used below.
N = {"C4": 261.63, "D4": 293.66, "E4": 329.63, "F4": 349.23, "G4": 392.0, "A4": 440.0, "B4": 493.88,
     "C5": 523.25, "D5": 587.33, "E5": 659.25, "F5": 698.46, "G5": 783.99, "A5": 880.0, "B5": 987.77,
     "C6": 1046.5, "E6": 1318.5, "G6": 1568.0, "C3": 130.81, "G3": 196.0, "E3": 164.81, "F3": 174.61}


def partials(f, dur, parts, attack=0.004, tau=0.5):
    """A struck tone: each (ratio, amp, decay-scale) partial rings and fades on its own."""
    t = np.arange(int(dur * RATE)) / RATE
    out = np.zeros_like(t)
    for ratio, amp, scale in parts:
        out += amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t / (tau * scale))
    return out * np.minimum(1, t / attack)


def glass(f, dur):
    return partials(f, dur, [(1, 1, 1), (2.756, 0.35, 0.45), (5.404, 0.12, 0.25)], tau=dur / 3.5)


def piano(f, dur):
    det = 1.0015
    tone = partials(f, dur, [(1, 1, 1), (2, 0.45, 0.6), (3, 0.22, 0.45), (4, 0.1, 0.3), (5, 0.05, 0.25)], attack=0.006, tau=dur / 3)
    return tone + 0.35 * partials(f * det, dur, [(1, 1, 1), (2, 0.3, 0.6)], attack=0.006, tau=dur / 3)


def marimba(f, dur):
    return partials(f, dur, [(1, 1, 1), (3.93, 0.28, 0.25), (9.2, 0.06, 0.12)], attack=0.002, tau=min(0.35, dur / 3))


def soft(f, dur):
    """A gentle sine with a slow swell: for the minimal style."""
    t = np.arange(int(dur * RATE)) / RATE
    env = np.minimum(1, t / 0.04) * np.exp(-t / (dur / 3))
    return (np.sin(2 * np.pi * f * t) + 0.15 * np.sin(4 * np.pi * f * t)) * env


def tick(_f, dur=0.03):
    """A tiny click, like a remote's soft key: filtered noise, a few milliseconds long."""
    n = int(dur * RATE)
    noise = rng.uniform(-1, 1, n)
    smooth = np.convolve(noise, np.ones(6) / 6, mode="same")
    return smooth * np.exp(-np.arange(n) / (RATE * 0.004))


def whoosh(_f, dur=0.45):
    n = int(dur * RATE)
    noise = rng.uniform(-1, 1, n)
    k = 40
    smooth = np.convolve(noise, np.ones(k) / k, mode="same")
    t = np.arange(n) / n
    return smooth * np.sin(np.pi * t) ** 2 * 3


INSTR = {"glass": glass, "piano": piano, "marimba": marimba, "soft": soft, "tick": tick, "whoosh": whoosh}


def room(dry, wet, length=0.9):
    """A small room, not a hall: short and light so sounds stay crisp."""
    n = int(length * RATE)
    impulse = rng.uniform(-1, 1, n) * (1 - np.arange(n) / n) ** 4
    size = 1 << int(np.ceil(np.log2(len(dry) + n)))
    rev = np.fft.irfft(np.fft.rfft(dry, size) * np.fft.rfft(impulse, size), size)[:len(dry) + n]
    rev /= np.max(np.abs(rev)) or 1
    out = np.zeros(len(dry) + n)
    out[:len(dry)] += dry / (np.max(np.abs(dry)) or 1)
    return out + rev * wet


def render(events, seconds, peak, wet):
    buf = np.zeros(int(seconds * RATE))
    for inst, note, at, dur, gain in events:
        f = N.get(note, 0) if isinstance(note, str) else note
        s = INSTR[inst](f, dur) * gain
        i = int(at * RATE)
        buf[i:i + len(s)] += s[:len(buf) - i]
    out = room(buf, wet)
    end = np.max(np.nonzero(np.abs(out) > 2e-4)) + 1
    out = out[:end]
    fade = min(len(out), int(0.03 * RATE))
    out[-fade:] *= np.linspace(1, 0, fade)
    return out / np.max(np.abs(out)) * peak


def happy_birthday(inst, beat, gain, octave_up=False):
    tune = [("G4", 0.75), ("G4", 0.25), ("A4", 1), ("G4", 1), ("C5", 1), ("B4", 2),
            ("G4", 0.75), ("G4", 0.25), ("A4", 1), ("G4", 1), ("D5", 1), ("C5", 2)]
    ev, at = [], 0.0
    for note, beats in tune:
        f = N[note] * (2 if octave_up else 1)
        ev.append((inst, f, at, max(0.5, beats * beat * 1.6), gain))
        at += beats * beat
    return ev, at + 1.2


def styles():
    s = {}
    # 1. Glass: bright, short, like Apple TV.
    hb, hb_len = happy_birthday("glass", 0.3, 0.5, octave_up=True)
    s["1-glass"] = {
        "sig": ([("glass", "C6", 0, 1.6, 0.5), ("glass", "E6", 0.14, 1.6, 0.45), ("glass", "G6", 0.28, 2.0, 0.45), ("soft", "C4", 0, 1.8, 0.3)], 2.6, 0.8, 0.18),
        "focus": ([("tick", 0, 0, 0.03, 1.0)], 0.1, 0.35, 0.05),
        "pick": ([("glass", "G5", 0, 0.9, 0.6), ("glass", "C6", 0.09, 1.2, 0.6)], 1.4, 0.7, 0.15),
        "open": ([("glass", "E5", 0, 0.6, 0.6), ("whoosh", 0, 0, 0.35, 0.25)], 0.8, 0.55, 0.12),
        "bday": (hb, hb_len, 0.8, 0.15),
        "night": ([("glass", "G5", 0, 1.4, 0.5), ("glass", "E5", 0.35, 1.4, 0.5), ("glass", "C5", 0.7, 2.2, 0.5)], 3.2, 0.6, 0.2),
    }
    # 2. Piano: warm and calm.
    hb, hb_len = happy_birthday("piano", 0.36, 0.5)
    s["2-piano"] = {
        "sig": ([("piano", "C4", 0, 2.4, 0.5), ("piano", "G4", 0.18, 2.2, 0.45), ("piano", "E5", 0.36, 2.4, 0.45), ("piano", "C3", 0, 2.6, 0.35)], 3.0, 0.8, 0.22),
        "focus": ([("piano", "C6", 0, 0.12, 0.25)], 0.2, 0.25, 0.05),
        "pick": ([("piano", "E5", 0, 1.2, 0.5), ("piano", "G5", 0.1, 1.4, 0.5)], 1.6, 0.7, 0.2),
        "open": ([("piano", "G4", 0, 0.9, 0.5), ("piano", "D5", 0.07, 0.9, 0.4)], 1.1, 0.6, 0.18),
        "bday": (hb, hb_len, 0.8, 0.2),
        "night": ([("piano", "E5", 0, 1.8, 0.45), ("piano", "C5", 0.45, 1.8, 0.45), ("piano", "G4", 0.9, 2.6, 0.45), ("piano", "C3", 0.9, 2.6, 0.3)], 3.8, 0.6, 0.25),
    }
    # 3. Marimba: wooden and friendly.
    hb, hb_len = happy_birthday("marimba", 0.28, 0.6)
    s["3-marimba"] = {
        "sig": ([("marimba", n, i * 0.11, 0.8, 0.6) for i, n in enumerate(("C5", "E5", "G5", "C6"))], 1.6, 0.8, 0.12),
        "focus": ([("marimba", "G5", 0, 0.15, 0.4)], 0.25, 0.3, 0.04),
        "pick": ([("marimba", "C5", 0, 0.6, 0.6), ("marimba", "G5", 0.08, 0.7, 0.6)], 1.0, 0.7, 0.1),
        "open": ([("marimba", "E5", 0, 0.5, 0.6), ("marimba", "A5", 0.07, 0.5, 0.5)], 0.8, 0.6, 0.1),
        "bday": (hb, hb_len, 0.8, 0.12),
        "night": ([("marimba", n, i * 0.3, 0.9, 0.5) for i, n in enumerate(("G5", "E5", "C5", "G4"))], 2.4, 0.55, 0.15),
    }
    # 4. Minimal: barely there; a soft click to move and a quiet tone to choose.
    hb, hb_len = happy_birthday("soft", 0.36, 0.5)
    s["4-minimal"] = {
        "sig": ([("soft", "C5", 0, 1.6, 0.4), ("soft", "G5", 0.2, 1.6, 0.3), ("whoosh", 0, 0, 0.6, 0.15)], 2.0, 0.55, 0.15),
        "focus": ([("tick", 0, 0, 0.025, 0.7)], 0.08, 0.2, 0.0),
        "pick": ([("soft", "E5", 0, 0.5, 0.5)], 0.7, 0.45, 0.1),
        "open": ([("whoosh", 0, 0, 0.4, 0.5)], 0.5, 0.4, 0.05),
        "bday": (hb, hb_len, 0.6, 0.15),
        "night": ([("soft", "E5", 0, 1.2, 0.4), ("soft", "C5", 0.5, 1.8, 0.4)], 2.6, 0.45, 0.15),
    }
    # The quieter everyday moments, in each style's own instrument.
    for name, inst, wet in (("1-glass", "glass", 0.1), ("2-piano", "piano", 0.12), ("3-marimba", "marimba", 0.06), ("4-minimal", "soft", 0.05)):
        s[name].update(extras(inst, wet))
    return s


def extras(inst, wet):
    """back, toggle, menu, page, note and edge: short, soft, and lower in volume than choosing or opening."""
    small = 0.35 if inst == "soft" else 0.5
    return {
        # Back: the same two notes as choosing, but falling.
        "back": ([(inst, "G5", 0, 0.35, small), (inst, "D5", 0.07, 0.45, small)], 0.7, 0.4, wet),
        # A switch flipping: one bright, very short note.
        "toggle": ([(inst, "A5", 0, 0.18, small), ("tick", 0, 0, 0.02, 0.3)], 0.35, 0.35, wet * 0.5),
        # A menu rising into view.
        "menu": ([(inst, "C5", 0, 0.4, small), (inst, "E5", 0.05, 0.45, small), (inst, "G5", 0.1, 0.55, small)], 0.9, 0.45, wet),
        # Turning a page: air with a soft note.
        "page": ([("whoosh", 0, 0, 0.3, 0.35), (inst, "E5", 0.12, 0.4, small * 0.6)], 0.7, 0.35, wet),
        # A note from a phone: a friendly two-tone, like a message arriving.
        "note": ([(inst, "E6" if inst == "glass" else "E5", 0, 0.6, small), (inst, "C6" if inst == "glass" else "C5", 0.16, 0.9, small)], 1.4, 0.55, wet * 1.5),
        # The end of a row: a low, muted bump that says "nothing further".
        "edge": ([("soft", "G3", 0, 0.14, 0.6)], 0.25, 0.25, 0.0),
    }


def write(path, out):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((out * 32767).astype("<i2").tobytes())


OUT = Path(sys.argv[1])
ORDER = ["sig", "focus", "focus", "focus", "edge", "pick", "menu", "toggle", "page", "back", "open", "note", "bday", "night"]
for name, sounds in styles().items():
    d = OUT / name
    d.mkdir(parents=True, exist_ok=True)
    rendered = {k: render(*v) for k, v in sounds.items()}
    for k, v in rendered.items():
        write(d / f"{k}.wav", v)
    gap = np.zeros(int(0.7 * RATE))
    short = np.zeros(int(0.25 * RATE))
    demo = []
    for k in ORDER:
        demo += [rendered[k], short if k in ("focus", "edge", "toggle") else gap]
    write(OUT / f"demo-{name}.wav", np.concatenate(demo))
    print(name, "done")

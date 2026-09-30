"""Genera i suoni dell'app in app/src/main/res/raw (WAV 16 bit mono). Solo libreria standard.

    python tools/generate_sounds.py
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44_100
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def mix(base, layer, at):
    start = int(RATE * at)
    if len(base) < start + len(layer):
        base.extend([0.0] * (start + len(layer) - len(base)))
    for i, v in enumerate(layer):
        base[start + i] += v
    return base


def glide(f0, f1, seconds, decay=4.0):
    """Sinusoide con glissando e decadimento esponenziale (i suoni dei tocchi)."""
    n, phase, out = int(RATE * seconds), 0.0, []
    for i in range(n):
        p = i / n
        phase += 2 * math.pi * (f0 + (f1 - f0) * p) / RATE
        attack = min(1.0, i / (RATE * 0.004))
        out.append(math.sin(phase) * attack * math.exp(-decay * p))
    return out


def bell(freq, seconds, decay=5.0):
    """Campanella: fondamentale + armoniche inarmoniche, leggero vibrato."""
    n, out = int(RATE * seconds), []
    for i in range(n):
        t = i / RATE
        env = min(1.0, i / (RATE * 0.003)) * math.exp(-decay * t)
        vib = 1 + 0.004 * math.sin(2 * math.pi * 6 * t)
        v = (math.sin(2 * math.pi * freq * vib * t)
             + 0.45 * math.sin(2 * math.pi * freq * 2.76 * t) * math.exp(-3 * t)
             + 0.25 * math.sin(2 * math.pi * freq * 5.4 * t) * math.exp(-6 * t))
        out.append(v * env)
    return out


def zap(seconds=0.32):
    """Saetta: dente di sega che precipita da acuto a grave, con crepitio di rumore."""
    rnd = random.Random(7)
    n, phase, out = int(RATE * seconds), 0.0, []
    for i in range(n):
        p = i / n
        freq = 3200 * (60 / 3200) ** p  # discesa esponenziale
        phase = (phase + freq / RATE) % 1.0
        saw = 2 * phase - 1
        crackle = rnd.uniform(-1, 1) * (1 if rnd.random() < 0.35 else 0.2)
        env = min(1.0, i / (RATE * 0.002)) * math.exp(-5 * p)
        out.append((0.7 * saw + 0.5 * crackle * (1 - p)) * env)
    return out


def sparkle(seconds, count, seed):
    """Brillantini: tanti piccoli "tin" acutissimi sparsi a caso."""
    rnd = random.Random(seed)
    out = silence(seconds)
    for _ in range(count):
        mix(out, [v * 0.18 for v in bell(rnd.uniform(3000, 6000), 0.12, decay=35)], rnd.uniform(0, seconds - 0.15))
    return out


def write(name, samples, peak=0.85):
    top = max(abs(v) for v in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(v / top * peak * 32767)) for v in samples)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    write("ui_pop", glide(440, 880, 0.11))
    write("ui_ding", mix(glide(988, 988, 0.11), glide(1319, 1319, 0.14), 0.11))
    write("ui_back", glide(660, 392, 0.11))

    # Notifica: saetta, poi arpeggio magico che sale (Do-Mi-Sol-Do-Mi) con brillantini.
    magic = zap()
    for k, freq in enumerate([1047, 1319, 1568, 2093, 2637]):
        mix(magic, [v * 0.5 for v in bell(freq, 0.9)], 0.22 + k * 0.075)
    mix(magic, sparkle(1.0, 28, seed=3), 0.25)
    fade = int(RATE * 0.15)
    for i in range(fade):
        magic[-fade + i] *= 1 - i / fade
    write("notification_magic", magic)


if __name__ == "__main__":
    main()

"""Synthesizes a rooster crow and hen clucks as WAV files (additive synthesis through vocal formants)."""
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 22050
rng = np.random.default_rng(7)


def formant_gain(freq, formants):
    g = np.zeros_like(freq)
    for fc, bw, amp in formants:
        g += amp / (1 + ((freq - fc) / bw) ** 2)
    return g


def voice(f0, amp, formants, rough=0.0, nharm=24):
    """f0, amp: per-sample arrays. Harmonics weighted by a formant envelope, with optional roughness."""
    phase = 2 * np.pi * np.cumsum(f0) / SR
    out = np.zeros_like(f0)
    for k in range(1, nharm + 1):
        fk = f0 * k
        g = formant_gain(fk, formants) * (fk < SR / 2 - 500)
        out += g * np.sin(k * phase + rng.uniform(0, 6.28)) / k ** 0.35
    if rough:
        # irregular amplitude flutter gives the throaty rasp of a bird call
        n = len(f0)
        jitter = np.interp(np.arange(n), np.linspace(0, n, n // 60 + 2), rng.normal(0, 1, n // 60 + 2))
        out *= 1 + rough * np.sin(2 * np.pi * 70 * np.arange(n) / SR + jitter)
    out /= np.max(np.abs(out)) + 1e-9
    return out * amp


def env_segments(segs, total):
    """segs: list of (start, end, f_start, f_end, level). Returns f0 and amplitude arrays."""
    n = int(total * SR)
    f0 = np.full(n, 400.0)
    amp = np.zeros(n)
    for s, e, fa, fb, lvl in segs:
        i0, i1 = int(s * SR), int(e * SR)
        m = i1 - i0
        x = np.linspace(0, 1, m)
        f0[i0:i1] = fa + (fb - fa) * np.sin(x * np.pi / 2)
        att = min(0.02, (e - s) / 4)
        a = np.minimum(1, np.minimum(x * (e - s) / att, (1 - x) * (e - s) / 0.04))
        amp[i0:i1] = np.maximum(amp[i0:i1], a * lvl)
    # carry pitch through gaps so phase stays continuous
    return f0, amp


def rooster():
    total = 2.25
    segs = [
        (0.00, 0.17, 560, 720, 0.75),   # cock
        (0.20, 0.30, 640, 660, 0.6),    # a
        (0.33, 0.55, 700, 860, 0.9),    # doo
        (0.57, 0.72, 860, 780, 0.75),   # dle
        (0.75, 2.05, 820, 900, 1.0),    # doooo (shaped below)
    ]
    f0, amp = env_segments(segs, total)
    t = np.arange(len(f0)) / SR
    last = (t >= 0.75) & (t < 2.05)
    x = (t[last] - 0.75) / 1.3
    f0[last] = 820 + 90 * np.sin(np.minimum(x * 3, 1) * np.pi / 2) - 330 * np.clip((x - 0.55) / 0.45, 0, 1) ** 1.6
    f0 *= 1 + 0.025 * np.sin(2 * np.pi * 6.5 * t)  # vibrato
    amp[last] *= np.clip(1.15 - 0.6 * x, 0, 1)
    formants = [(1100, 260, 1.0), (2300, 420, 0.65), (3400, 600, 0.25)]
    y = voice(f0, amp, formants, rough=0.35)
    # a little breath noise
    sos = butter(2, [1500, 5000], btype="band", fs=SR, output="sos")
    y += 0.05 * sosfilt(sos, rng.normal(0, 1, len(y))) * amp
    return y


def cluck_phrase(t0, total_n, out_f0, out_amp, pattern):
    for (dt, dur, fa, fb, lvl) in pattern:
        s = t0 + dt
        i0, i1 = int(s * SR), int((s + dur) * SR)
        if i1 > total_n:
            continue
        m = i1 - i0
        x = np.linspace(0, 1, m)
        out_f0[i0:i1] = fa + (fb - fa) * x
        a = np.minimum(1, x * dur / 0.006) * np.exp(-x * 3.2)
        out_amp[i0:i1] = np.maximum(out_amp[i0:i1], a * lvl)


def clucks():
    total = 2.6
    n = int(total * SR)
    f0 = np.full(n, 380.0)
    amp = np.zeros(n)
    buk = lambda dt, f=340, l=0.7: (dt, 0.075, f + 40, f - 30, l)
    phrase1 = [buk(0.0), buk(0.16, 330), buk(0.30, 350), (0.46, 0.09, 430, 470, 0.7), (0.57, 0.36, 560, 760, 1.0)]
    phrase2 = [buk(0.0, 360, 0.6), buk(0.14, 340, 0.65), (0.30, 0.09, 440, 480, 0.7), (0.41, 0.42, 600, 820, 1.0)]
    cluck_phrase(0.02, n, f0, amp, phrase1)
    cluck_phrase(1.15, n, f0, amp, phrase2)
    # bagawk: rise then fall
    t = np.arange(n) / SR
    for s, d in ((0.59, 0.36), (1.56, 0.42)):
        sel = (t >= s) & (t < s + d)
        x = (t[sel] - s) / d
        f0[sel] = f0[sel] * (1 - 0.25 * np.clip((x - 0.5) / 0.5, 0, 1))
        amp[sel] *= np.clip(1.4 - x, 0, 1)
    formants = [(900, 220, 1.0), (1900, 380, 0.55), (3000, 500, 0.2)]
    y = voice(f0, amp, formants, rough=0.5)
    sos = butter(2, [800, 3500], btype="band", fs=SR, output="sos")
    y += 0.12 * sosfilt(sos, rng.normal(0, 1, n)) * amp
    return y


def write(name, y):
    y = y / (np.max(np.abs(y)) + 1e-9) * 0.89
    fade = int(0.03 * SR)
    y[-fade:] *= np.linspace(1, 0, fade)
    wavfile.write(name, SR, (y * 32767).astype(np.int16))


if __name__ == "__main__":
    import sys
    out = sys.argv[1]
    write(f"{out}/rooster.wav", rooster())
    write(f"{out}/clucks.wav", clucks())

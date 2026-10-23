"""The phone's on-device colour fingerprint (Android `ai/PostAppearance.kt`, web `src/ai/PostAppearance.ts`), in numpy.

27 values: an 18-bin folded HSV histogram, centre-weighted, plus the mean RGB of three vertical columns. It is what
the app falls back to when this PC is offline, so the evaluation measures it as "Baseline A" with the same maths.
`tests/test_fingerprint.py` checks it against fingerprints computed by real Android bitmaps.
"""
from __future__ import annotations

import io

import numpy as np
from PIL import Image

HIST_BINS = 18
SIZE = HIST_BINS + 9
HUE_BINS = 12


def _hsv(rgb: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Android's Color.colorToHSV: hue in degrees 0..360, saturation and value 0..1."""
    r, g, b = (rgb[..., i].astype(np.float64) / 255.0 for i in range(3))
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    d = mx - mn
    s = np.where(mx > 0, d / np.where(mx > 0, mx, 1), 0)
    safe = np.where(d > 0, d, 1)
    h = np.where(mx == r, (g - b) / safe % 6, np.where(mx == g, (b - r) / safe + 2, (r - g) / safe + 4)) * 60
    h = np.where(d > 0, h, 0) % 360
    # Android hands back floats: round to float32 like Kotlin and the web (Math.fround), compare in double.
    f = lambda a: a.astype(np.float32).astype(np.float64)
    return f(h), f(s), f(mx)


def hist_bins(rgb: np.ndarray) -> np.ndarray:
    """CatAnalyzer.histBin for every pixel: 48 colour bins, then 6 grey levels (48..53)."""
    h, s, v = _hsv(rgb)
    grey = (s < 0.18) | (v < 0.15)
    hbin = np.clip((h / 360.0 * HUE_BINS).astype(int), 0, HUE_BINS - 1)
    colour = hbin * 4 + (s >= 0.5) * 2 + (v >= 0.55)
    return np.where(grey, HUE_BINS * 4 + np.clip((v * 6).astype(int), 0, 5), colour)


def scale_to_max(im: Image.Image, max_dim: int) -> Image.Image:
    w, h = im.size
    if max(w, h) <= max_dim:
        return im
    scale = max_dim / max(w, h)
    return im.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.BILINEAR)


def describe_rgb(rgb: np.ndarray) -> np.ndarray:
    """The 27 values for an already scaled RGB array (height, width, 3)."""
    hgt, wid = rgb.shape[:2]
    ys, xs = np.mgrid[0:hgt, 0:wid]
    dx = (xs - wid / 2.0) / (wid * 0.3)
    dy = (ys - hgt / 2.0) / (hgt * 0.32)
    weight = np.exp(-(dx * dx + dy * dy) / 2)
    bins = hist_bins(rgb)
    folded = np.where(bins < 48, bins // 4, 12 + bins - 48)
    values = np.zeros(SIZE)
    np.add.at(values, folded.ravel(), weight.ravel())
    total = weight.sum()
    values[:HIST_BINS] /= max(total, 1e-9)
    cell = np.clip(xs * 3 // wid, 0, 2)
    for c in range(3):
        m = cell == c
        wsum = max(weight[m].sum(), 1e-9)
        for ch in range(3):
            values[HIST_BINS + c * 3 + ch] = (rgb[..., ch][m] / 255.0 * weight[m]).sum() / wsum
    return np.round(values * 10_000) / 10_000


def describe(image: Image.Image | bytes) -> np.ndarray:
    """Like PostAppearance.describe: the photo scaled to at most 128 px, then the 27 values."""
    if isinstance(image, (bytes, bytearray)):
        image = Image.open(io.BytesIO(image), formats=("JPEG",))  # app photos are JPEG (recognition.APP_FORMATS)
    rgb = np.asarray(scale_to_max(image.convert("RGB"), 128), dtype=np.uint8)
    return describe_rgb(rgb)


def valid(v) -> bool:
    return v is not None and len(v) == SIZE and all(np.isfinite(x) and 0 <= x <= 1 for x in v)


def similarity(a, b) -> float:
    """PostAppearance.similarity: 0.8 histogram intersection + 0.2 colour layout, 0..1 (0 when either is missing)."""
    if not valid(a) or not valid(b):
        return 0.0
    a, b = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
    histogram = float(np.clip(np.minimum(a[:HIST_BINS], b[:HIST_BINS]).sum(), 0, 1))
    layout = float(np.clip(1 - np.abs(a[HIST_BINS:] - b[HIST_BINS:]).sum() / 9, 0, 1))
    return 0.8 * histogram + 0.2 * layout

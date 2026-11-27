#!/usr/bin/env python3
"""Shared compact bitmap-letter renderer for NEXORA chat glyphs (ranks and clan tags).

Bedrock sizes a glyph from the left edge of its 16x16 cell to the right-most
opaque pixel. The old generator centred every letter and stretched it to the
full cell, so each letter took ~2x the width of normal chat text. Here every
letter is baseline-aligned, left-aligned, and only as wide as it really is, so
prefixes stay short while the letters keep an outline, a bevel and a gradient.
Requires Pillow.
"""
from pathlib import Path
import colorsys
from PIL import Image, ImageDraw, ImageFont

CELL = 16
SCALE = 8        # supersampling factor for the letter mask
CAP = 9          # capital letter height in cell pixels (normal chat caps are ~7 px)
BASELINE = 13    # row of the baseline: caps fill rows 4..12, descenders 13..15
PAD = 1          # left column reserved for the outline
SPACE = 4        # width of a plain space between words, in cell pixels
MAX_FACE_W = 11  # widest letter body that still fits the cell with outline

_HERE = Path(__file__).resolve().parent
FONT_CANDIDATES = [
    _HERE / "fonts" / "FiraSans-ExtraBold.ttf",
    Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
]

_font = None


def _load_font():
    global _font
    if _font is not None:
        return _font
    for path in FONT_CANDIDATES:
        if path.exists():
            probe = ImageFont.truetype(str(path), 100)
            cap100 = -probe.getbbox("H", anchor="ls")[1]
            _font = ImageFont.truetype(str(path), max(8, round(100 * CAP * SCALE / cap100)))
            return _font
    raise SystemExit("No usable font: put FiraSans-ExtraBold.ttf into resource-pack/tools/fonts/")


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


def gradient(stops, t):
    """Piecewise-linear colour along `stops` for t in 0..1."""
    t = max(0.0, min(1.0, t))
    if len(stops) == 1:
        return tuple(float(c) for c in stops[0])
    scaled = t * (len(stops) - 1)
    i = min(int(scaled), len(stops) - 2)
    return mix(stops[i], stops[i + 1], scaled - i)


def vivid(rgb, boost=1.12):
    """Push saturation up a little so gradients stay punchy on dark chat backgrounds."""
    h, s, v = colorsys.rgb_to_hsv(*(max(0.0, min(255.0, c)) / 255 for c in rgb))
    r, g, b = colorsys.hsv_to_rgb(h, min(1.0, s * boost), min(1.0, v * 1.03))
    return (r * 255, g * 255, b * 255)


_mask_cache = {}


def letter_mask(ch):
    """Return an 'L' mask (width x CELL) of `ch`, left-aligned on the shared baseline."""
    if ch in _mask_cache:
        return _mask_cache[ch]
    font = _load_font()
    canvas = Image.new("L", (CELL * SCALE * 2, CELL * SCALE), 0)
    ImageDraw.Draw(canvas).text((4 * SCALE, BASELINE * SCALE), ch, font=font, fill=255, anchor="ls")
    box = canvas.getbbox()
    if box is None:
        mask = Image.new("L", (1, CELL), 0)
    else:
        width = max(1, -(-(box[2] - box[0]) // SCALE))
        strip = Image.new("L", (width * SCALE, CELL * SCALE), 0)
        strip.paste(canvas.crop((box[0], 0, box[2], CELL * SCALE)), (0, 0))
        mask = strip.resize((min(width, MAX_FACE_W), CELL), Image.Resampling.LANCZOS)
        # Crisper edges read better at this size than soft antialiasing.
        mask = mask.point(lambda a: 0 if a < 45 else min(255, int((a - 45) * 255 / 150)))
    _mask_cache[ch] = mask
    return mask


def face_width(ch):
    return letter_mask(ch).size[0]


def used_width(ch):
    """Columns from the cell's left edge to the last opaque pixel (outline included)."""
    return PAD + face_width(ch) + 1


def render(ch, color_at):
    """Render one 16x16 RGBA glyph. `color_at(x)` gives the base RGB for glyph column x."""
    mask = letter_mask(ch)
    w = mask.size[0]
    face = [[0] * CELL for _ in range(CELL)]
    src = mask.load()
    for y in range(CELL):
        for x in range(w):
            face[y][PAD + x] = src[x, y]

    def alpha(x, y):
        return face[y][x] if 0 <= x < CELL and 0 <= y < CELL else 0

    outline = [[max(alpha(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                for x in range(CELL)] for y in range(CELL)]
    top = BASELINE - CAP
    out = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    px = out.load()
    for y in range(CELL):
        for x in range(CELL):
            f, o = face[y][x], outline[y][x]
            shadow = outline[y - 1][x] if y > 0 else 0
            base = vivid(color_at(max(0, min(w - 1, x - PAD))))
            dark = mix(tuple(c * 0.22 for c in base), (10, 8, 28), 0.55)
            # Back to front: soft drop shadow, dark tinted outline, then the lit face.
            layer = (10, 8, 28, int(shadow * 0.45)) if shadow else (0, 0, 0, 0)
            if o:
                layer = _over((*dark, o), layer)
            if f:
                v = max(0.0, min(1.0, (y - top) / (CAP - 1)))
                rgb = mix(base, (255, 255, 255), 0.34 * (1 - v) ** 2)
                rgb = mix(rgb, (0, 0, 0), 0.30 * v ** 1.6)
                if alpha(x, y - 1) < 80:      # lit rim along the upper edge
                    rgb = mix(rgb, (255, 255, 255), 0.30)
                if alpha(x, y + 1) < 80:      # shaded lower edge
                    rgb = mix(rgb, (0, 0, 0), 0.20)
                layer = _over((*rgb, f), layer)
            px[x, y] = tuple(max(0, min(255, round(c))) for c in layer)
    return out


def _over(top, bottom):
    ta, ba = top[3] / 255, bottom[3] / 255
    oa = ta + ba * (1 - ta)
    if oa <= 0:
        return (0, 0, 0, 0)
    rgb = [(top[i] * ta + bottom[i] * ba * (1 - ta)) / oa for i in range(3)]
    return (*rgb, oa * 255)

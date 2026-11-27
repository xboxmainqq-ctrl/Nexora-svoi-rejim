#!/usr/bin/env python3
"""Generate bitmap-font glyph pages for true clan-name gradients.

Each clan character is a pre-rendered coloured glyph, not a Minecraft §x code.
The Java plugin maps each character to the same codepoint formula below.
Requires Pillow; the generated glyph pages are packed into NEXORA_Beasts_RP.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "NEXORA_Beasts_RP"
CHARSET = (
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
    "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    "0123456789_-"
)
BASE = 0xE300
STEPS = 10
PALETTES = [
    ("sunset", (255, 74, 142), (255, 177, 82)),
    ("ocean", (62, 224, 255), (54, 104, 255)),
    ("neon", (255, 58, 222), (87, 92, 255)),
    ("royal", (117, 91, 255), (255, 205, 104)),
]
CELL = 16
SHEET = 256
SCALE = 4
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def shade(rgb, amount):
    return tuple(max(0, min(255, round(c * amount))) for c in rgb)


def make_glyph(ch, rgb, font):
    size = CELL * SCALE
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    if ch != " ":
        bbox = draw.textbbox((0, 0), ch, font=font, stroke_width=0)
        w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
        x = (size - w) // 2 - bbox[0]
        y = (size - h) // 2 - bbox[1] - 1
        draw.text((x, y), ch, font=font, fill=255)

    # A restrained dark outline keeps small letters legible on every chat background.
    shadow = Image.new("L", (size, size), 0)
    shadow.paste(mask, (SCALE, SCALE))
    shadow = shadow.filter(ImageFilterMax())
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shadow_px = shadow.load()
    mask_px = mask.load()
    out = result.load()
    for y in range(size):
        vertical = y / max(1, size - 1)
        face = mix(shade(rgb, 1.18), shade(rgb, 0.88), vertical)
        for x in range(size):
            if shadow_px[x, y] and not mask_px[x, y]:
                out[x, y] = (19, 18, 31, min(190, shadow_px[x, y]))
            elif mask_px[x, y]:
                out[x, y] = (*face, mask_px[x, y])
    return result.resize((CELL, CELL), Image.Resampling.LANCZOS)


def ImageFilterMax():
    # A tiny dilation implemented with Pillow's MaxFilter without importing more modules globally.
    from PIL import ImageFilter
    return ImageFilter.MaxFilter(3)


def build_all():
    font = ImageFont.truetype(FONT_PATH, 48)
    total = len(PALETTES) * STEPS * len(CHARSET)
    pages = {}
    for page_index in range((total + 255) // 256):
        pages[page_index] = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))

    for gradient_index, (name, start, end) in enumerate(PALETTES):
        for step in range(STEPS):
            t = step / (STEPS - 1)
            rgb = mix(start, end, t)
            for char_index, ch in enumerate(CHARSET):
                codepoint = BASE + ((gradient_index * STEPS + step) * len(CHARSET)) + char_index
                offset = codepoint - BASE
                page_index, slot = divmod(offset, 256)
                glyph = make_glyph(ch, rgb, font)
                x = (slot % 16) * CELL
                y = (slot // 16) * CELL
                pages[page_index].alpha_composite(glyph, (x, y))

    font_dir = ROOT / "font"
    font_dir.mkdir(parents=True, exist_ok=True)
    for page_index, sheet in pages.items():
        page = (BASE >> 8) + page_index
        path = font_dir / f"glyph_{page:02X}.png"
        sheet.save(path, optimize=True)
    print(f"Generated {total} clan-gradient glyphs in {len(pages)} pages (U+{BASE:04X}..U+{BASE + total - 1:04X}).")


if __name__ == "__main__":
    build_all()

#!/usr/bin/env python3
"""Generate bitmap-font glyph pages for true clan-name gradients.

Each clan character is a pre-rendered coloured glyph, not a Minecraft §x code.
The Java plugin maps each character to the same codepoint formula below.
Requires Pillow; the generated glyph pages are packed into NEXORA_Beasts_RP.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

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


def build_masks(font):
    """Rasterize each supported character only once, then reuse it for all gradients."""
    masks = {}
    for ch in CHARSET:
        mask = Image.new("L", (CELL * SCALE, CELL * SCALE), 0)
        draw = ImageDraw.Draw(mask)
        bbox = draw.textbbox((0, 0), ch, font=font)
        width, height = bbox[2] - bbox[0], bbox[3] - bbox[1]
        x = (CELL * SCALE - width) // 2 - bbox[0]
        y = (CELL * SCALE - height) // 2 - bbox[1] - 1
        draw.text((x, y), ch, font=font, fill=255)
        shadow = Image.new("L", mask.size, 0)
        shadow.paste(mask, (SCALE, SCALE))
        shadow = shadow.filter(ImageFilter.MaxFilter(3))
        masks[ch] = (
            mask.resize((CELL, CELL), Image.Resampling.LANCZOS),
            shadow.resize((CELL, CELL), Image.Resampling.LANCZOS),
        )
    return masks


def make_glyph(mask, shadow, rgb):
    face = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    face_alpha = mask.load()
    face_px = face.load()
    for y in range(CELL):
        row_color = mix(shade(rgb, 1.16), shade(rgb, 0.86), y / (CELL - 1))
        for x in range(CELL):
            alpha = face_alpha[x, y]
            if alpha:
                face_px[x, y] = (*row_color, alpha)
    outline = Image.new("RGBA", (CELL, CELL), (19, 18, 31, 0))
    outline.putalpha(shadow)
    return Image.alpha_composite(outline, face)


def build_all():
    font = ImageFont.truetype(FONT_PATH, 48)
    masks = build_masks(font)
    total = len(PALETTES) * STEPS * len(CHARSET)
    pages = {
        page: Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
        for page in range((total + 255) // 256)
    }

    for gradient_index, (_, start, end) in enumerate(PALETTES):
        for step in range(STEPS):
            rgb = mix(start, end, step / (STEPS - 1))
            for char_index, ch in enumerate(CHARSET):
                codepoint = BASE + ((gradient_index * STEPS + step) * len(CHARSET)) + char_index
                page_index, slot = divmod(codepoint - BASE, 256)
                glyph = make_glyph(*masks[ch], rgb)
                x = (slot % 16) * CELL
                y = (slot // 16) * CELL
                pages[page_index].alpha_composite(glyph, (x, y))

    font_dir = ROOT / "font"
    font_dir.mkdir(parents=True, exist_ok=True)
    for page_index, sheet in pages.items():
        page = (BASE >> 8) + page_index
        sheet.save(font_dir / f"glyph_{page:02X}.png", optimize=True)
    print(f"Generated {total} clan-gradient glyphs in {len(pages)} pages (U+{BASE:04X}..U+{BASE + total - 1:04X}).")


if __name__ == "__main__":
    build_all()

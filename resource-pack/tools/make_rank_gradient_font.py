#!/usr/bin/env python3
"""Generate independent bitmap gradients for NEXORA player privileges and staff ranks.

The glyphs use a reserved PUA range immediately after the clan font. Each
rank label has its own position-specific glyphs, so these palettes never
reuse the clan or one another's glyph mappings.
Requires Pillow.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1] / "NEXORA_Beasts_RP"
BASE = 0xF750
STEPS = 3
CELL = 16
SHEET = 256
SCALE = 4
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# start/end colors are intentionally separate from the clan palettes.
RANKS = [
    ("aurvex", "AURVEX", (92, 246, 212), (86, 200, 247)),
    ("zyreth", "ZYRETH", (255, 128, 200), (167, 139, 250)),
    ("veloria", "VELORIA", (255, 225, 166), (255, 159, 135)),
    ("kryon", "KRYON", (139, 231, 255), (213, 242, 255)),
    ("nymeris", "NYMERIS", (255, 138, 114), (255, 82, 107)),
    ("solvex", "SOLVEX", (255, 240, 106), (255, 181, 46)),
    ("eclyra", "ECLYRA", (121, 216, 255), (169, 155, 255)),
    ("nexorion", "NEXORION", (90, 140, 255), (181, 107, 255)),
    # Staff gradients are brighter and more metallic than player privileges.
    ("helper", "ХЕЛПЕР", (48, 242, 178), (86, 223, 255)),
    ("junior_moder", "МЛАДШИЙ МОДЕРАТОР", (67, 139, 255), (100, 244, 255)),
    ("moder", "МОДЕРАТОР", (134, 87, 255), (241, 139, 255)),
    ("senior_moder", "СТАРШИЙ МОДЕРАТОР", (255, 65, 108), (255, 192, 120)),
    ("admin", "АДМИНИСТРАТОР", (255, 196, 79), (255, 245, 198)),
    ("chief_admin", "ГЛАВНЫЙ АДМИНИСТРАТОР", (255, 255, 255), (155, 140, 255)),
]


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def shade(rgb, amount):
    return tuple(max(0, min(255, round(c * amount))) for c in rgb)


def build_masks(font):
    masks = {}
    charset = sorted({ch for _, label, _, _ in RANKS for ch in label if ch != " "})
    for ch in charset:
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
    total = sum(len(label.replace(" ", "")) for _, label, _, _ in RANKS) * STEPS
    first_page = BASE >> 8
    last_page = (BASE + total - 1) >> 8
    font_dir = ROOT / "font"
    font_dir.mkdir(parents=True, exist_ok=True)

    # Preserve the clan glyphs already occupying the lower part of glyph_F7.png.
    pages = {}
    for page in range(first_page, last_page + 1):
        path = font_dir / f"glyph_{page:02X}.png"
        if path.exists():
            pages[page] = Image.open(path).convert("RGBA")
        else:
            pages[page] = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))

    offset = 0
    for _, label, start, end in RANKS:
        for position, ch in enumerate(c for c in label if c != " "):
            for step in range(STEPS):
                codepoint = BASE + offset + position * STEPS + step
                page_index = codepoint >> 8
                slot = codepoint & 0xFF
                rgb = mix(start, end, step / (STEPS - 1))
                glyph = make_glyph(*masks[ch], rgb)
                x = (slot % 16) * CELL
                y = (slot // 16) * CELL
                pages[page_index].alpha_composite(glyph, (x, y))
        offset += len(label.replace(" ", "")) * STEPS

    for page, sheet in pages.items():
        sheet.save(font_dir / f"glyph_{page:02X}.png", optimize=True)
    print(f"Generated {total} independent privilege/staff glyphs (U+{BASE:04X}..U+{BASE + total - 1:04X}).")


if __name__ == "__main__":
    build_all()

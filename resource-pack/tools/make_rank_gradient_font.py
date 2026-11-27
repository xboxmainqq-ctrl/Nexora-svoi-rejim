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
STEPS = 5
CELL = 16
SHEET = 256
SCALE = 4
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_SIZE = 38

# start/end colors are intentionally separate from the clan palettes.
RANKS = [
    ("aurvex", "AURVEX", ((116, 83, 255), (224, 150, 255), (72, 220, 255))),
    ("zyreth", "ZYRETH", ((255, 74, 178), (255, 132, 226), (125, 105, 255))),
    ("veloria", "VELORIA", ((255, 126, 75), (255, 205, 119), (255, 100, 155))),
    ("kryon", "KRYON", ((55, 170, 255), (130, 235, 255), (190, 160, 255))),
    ("nymeris", "NYMERIS", ((255, 75, 95), (255, 126, 85), (190, 55, 145))),
    ("solvex", "SOLVEX", ((255, 205, 65), (255, 245, 145), (255, 125, 65))),
    ("eclyra", "ECLYRA", ((40, 220, 235), (110, 240, 255), (175, 115, 255))),
    ("nexorion", "NEXORION", ((100, 90, 255), (215, 135, 255), (255, 190, 235))),
    ("helper", "ХЕЛПЕР", ((35, 210, 135), (110, 255, 190), (60, 210, 255))),
    ("junior_moder", "МЛАДШИЙ МОДЕРАТОР", ((35, 105, 255), (70, 205, 255), (155, 245, 255))),
    ("moder", "МОДЕРАТОР", ((115, 65, 245), (190, 115, 255), (255, 140, 230))),
    ("senior_moder", "СТАРШИЙ МОДЕРАТОР", ((225, 45, 100), (255, 105, 145), (255, 195, 115))),
    ("admin", "АДМИНИСТРАТОР", ((230, 145, 45), (255, 215, 105), (255, 250, 205))),
    ("staff", "STAFF", ((255, 185, 45), (255, 235, 125), (255, 255, 225))),
]

def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def shade(rgb, amount):
    return tuple(max(0, min(255, round(c * amount))) for c in rgb)


def build_masks(font):
    masks = {}
    charset = sorted({ch for _, label, _ in RANKS for ch in label if ch != " "})
    for ch in charset:
        mask = Image.new("L", (CELL * SCALE, CELL * SCALE), 0)
        draw = ImageDraw.Draw(mask)
        bbox = draw.textbbox((0, 0), ch, font=font)
        width, height = bbox[2] - bbox[0], bbox[3] - bbox[1]
        glyph = Image.new("L", (width, height), 0)
        ImageDraw.Draw(glyph).text((-bbox[0], -bbox[1]), ch, font=font, fill=255)
        # Shorter glyph height, but almost full cell width: compact spacing without bulky letters.
        target_w = min((CELL - 1) * SCALE, max(1, width))
        target_h = min(12 * SCALE, max(1, height))
        glyph = glyph.resize((target_w, target_h), Image.Resampling.LANCZOS)
        mask.paste(glyph, ((CELL * SCALE - target_w) // 2, (CELL * SCALE - target_h) // 2))
        shadow = Image.new("L", mask.size, 0)
        shadow.paste(mask, (SCALE // 2, SCALE // 2))
        shadow = shadow.filter(ImageFilter.MaxFilter(3))
        masks[ch] = (mask.resize((CELL, CELL), Image.Resampling.LANCZOS),
                     shadow.resize((CELL, CELL), Image.Resampling.LANCZOS))
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
    font = ImageFont.truetype(FONT_PATH, FONT_SIZE)
    masks = build_masks(font)
    total = sum(len(label.replace(" ", "")) for _, label, _ in RANKS) * STEPS
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
    for _, label, stops in RANKS:
        for position, ch in enumerate(c for c in label if c != " "):
            for step in range(STEPS):
                codepoint = BASE + offset + position * STEPS + step
                page_index = codepoint >> 8
                slot = codepoint & 0xFF
                t = step / (STEPS - 1)
                rgb = mix(stops[0], stops[1], t * 2) if t <= 0.5 else mix(stops[1], stops[2], (t - 0.5) * 2)
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

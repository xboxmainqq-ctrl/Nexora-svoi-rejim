#!/usr/bin/env python3
"""Generate font/glyph_E2.png: anti-aliased gradient rarity lettering.

Needs Pillow and a Cyrillic-capable bold font (DejaVu Sans Bold by default).
The result is committed, so normal pack builds need neither.

Glyph layout: U+E200 + N, where N counts letters over RARITY_LABELS in order.
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "NEXORA_Beasts_RP"
FONT = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
RARITY_LABELS = [
    "ОБЫЧНОЕ", "НЕОБЫЧНОЕ", "РЕДКОЕ", "ЭПИЧЕСКОЕ", "ЛЕГЕНДАРНОЕ",
    "МИФИЧЕСКОЕ", "ДРЕВНЕЕ", "БОЖЕСТВЕННОЕ", "ЗАПРЕТНОЕ", "АБСОЛЮТНОЕ",
]
# (top-left light, middle, bottom-right dark) per rarity class.
GRADIENTS = [
    ((236, 239, 244), (172, 178, 188), (112, 120, 132)),   # common     steel
    ((205, 255, 172), (92, 222, 112), (30, 150, 82)),      # uncommon   green
    ((172, 242, 255), (70, 172, 255), (40, 100, 226)),     # rare       blue
    ((242, 192, 255), (190, 100, 255), (118, 50, 200)),    # epic       violet
    ((255, 246, 122), (255, 192, 52), (255, 110, 30)),     # legendary  yellow-orange
    ((255, 192, 236), (255, 112, 202), (190, 60, 216)),    # mythic     pink-magenta
    ((172, 255, 236), (62, 216, 202), (26, 136, 152)),     # ancient    jade
    ((255, 255, 238), (255, 236, 152), (236, 186, 72)),    # divine     pearl-gold
    ((255, 152, 132), (236, 52, 62), (132, 12, 42)),       # forbidden  crimson
    ((255, 255, 255), (192, 172, 255), (126, 92, 236)),    # absolute   astral
]
OUTLINE = (22, 18, 32)
CELL, SIZE = 16, 256
BASELINE, FONT_PX = 12, 10


def lerp3(stops, t):
    t = min(1.0, max(0.0, t))
    a, b, u = (stops[0], stops[1], t * 2) if t < 0.5 else (stops[1], stops[2], (t - 0.5) * 2)
    return tuple(round(a[i] + (b[i] - a[i]) * u) for i in range(3))


def main():
    font = ImageFont.truetype(FONT, FONT_PX)
    sheet = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    index = 0
    for label, stops in zip(RARITY_LABELS, GRADIENTS):
        n = len(label)
        for pos, ch in enumerate(label):
            mask = Image.new("L", (CELL, CELL), 0)
            ImageDraw.Draw(mask).text((1, BASELINE), ch, fill=255, font=font, anchor="ls")
            px = mask.load()
            cell = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
            cp = cell.load()
            # soft 1px outline/shadow under the letter
            for y in range(CELL):
                for x in range(CELL):
                    a = px[x, y]
                    for dx, dy in ((1, 1), (1, 0), (0, 1)):
                        sx, sy = x - dx, y - dy
                        if 0 <= sx < CELL and 0 <= sy < CELL and px[sx, sy] > 0 and a < 255:
                            cp[x, y] = (*OUTLINE, max(cp[x, y][3], min(235, px[sx, sy])))
            for y in range(CELL):
                for x in range(CELL):
                    a = px[x, y]
                    if a == 0:
                        continue
                    tv = (y - (BASELINE - 8)) / 9.0
                    th = (pos + x / CELL) / n
                    rgb = lerp3(stops, 0.62 * th + 0.38 * tv)
                    alpha = min(255, int(a * 1.35))
                    base = cp[x, y]
                    if base[3]:
                        k = alpha / 255.0
                        rgb = tuple(round(rgb[i] * k + base[i] * (1 - k)) for i in range(3))
                        alpha = max(alpha, base[3])
                    cp[x, y] = (*rgb, alpha)
            sheet.paste(cell, ((index % 16) * CELL, (index // 16) * CELL))
            index += 1
    out = ROOT / "font" / "glyph_E2.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, optimize=True)
    print(f"wrote {out} ({index} glyphs)")


if __name__ == "__main__":
    main()

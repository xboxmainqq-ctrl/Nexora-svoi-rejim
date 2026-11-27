#!/usr/bin/env python3
"""Generate bitmap-font glyph pages for true clan-name gradients.

Each clan character is a pre-rendered coloured glyph, not a Minecraft §x code.
The Java plugin maps each character to the same codepoint formula below:
    U+E300 + ((palette * STEPS + step) * len(CHARSET)) + charIndex
Letters are compact and bevelled (see glyph_style.py). Palette order must match
GRADIENT_IDS in ClanModule.java. Requires Pillow.
"""
from pathlib import Path
from PIL import Image

import glyph_style as gs

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
    ("sunset", [(255, 50, 120), (255, 120, 60), (255, 205, 70)]),
    ("ocean", [(40, 235, 255), (50, 150, 255), (90, 90, 255)]),
    ("neon", [(255, 50, 230), (170, 70, 255), (70, 120, 255)]),
    ("royal", [(130, 80, 255), (220, 110, 255), (255, 200, 100)]),
]
SHEET = 256


def build_all():
    total = len(PALETTES) * STEPS * len(CHARSET)
    pages = {
        page: Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
        for page in range((total + 255) // 256)
    }
    for palette_index, (_, stops) in enumerate(PALETTES):
        for step in range(STEPS):
            rgb = gs.gradient(stops, step / (STEPS - 1))
            for char_index, ch in enumerate(CHARSET):
                codepoint = BASE + ((palette_index * STEPS + step) * len(CHARSET)) + char_index
                page_index, slot = divmod(codepoint - BASE, 256)
                glyph = gs.render(ch, lambda gx, rgb=rgb: rgb)
                pages[page_index].paste(glyph, ((slot % 16) * gs.CELL, (slot // 16) * gs.CELL))

    font_dir = ROOT / "font"
    font_dir.mkdir(parents=True, exist_ok=True)
    for page_index, sheet in pages.items():
        page = (BASE >> 8) + page_index
        sheet.save(font_dir / f"glyph_{page:02X}.png", optimize=True)
    print(f"Generated {total} clan-gradient glyphs in {len(pages)} pages (U+{BASE:04X}..U+{BASE + total - 1:04X}).")


if __name__ == "__main__":
    build_all()

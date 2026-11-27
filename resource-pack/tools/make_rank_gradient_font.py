#!/usr/bin/env python3
"""Generate bitmap gradients for NEXORA player privileges and staff ranks.

Every letter of every rank label is its own glyph, placed at
U+F750 + <rank offset> + <letter position>. The colour runs smoothly across
the whole word (each glyph is cut from one continuous gradient), so there is
no stepped banding between letters. Letters are compact, outlined and bevelled
(see glyph_style.py). Offsets are cumulative letter counts in RANKS order and
must match NEXORADonate / NEXORAAdminRanks (checked by verify_rank_glyph_map.py).
Requires Pillow.
"""
from pathlib import Path
from PIL import Image

import glyph_style as gs

ROOT = Path(__file__).resolve().parents[1] / "NEXORA_Beasts_RP"
BASE = 0xF750
SHEET = 256

# (id, label baked into the font, gradient stops left -> right).
# Donate privileges first, then the staff ladder. Do not reorder without
# regenerating the pack and updating both plugins.
RANKS = [
    ("aurvex", "AURVEX", [(112, 64, 255), (214, 96, 255), (60, 200, 255), (120, 255, 235)]),
    ("zyreth", "ZYRETH", [(255, 40, 150), (255, 96, 220), (150, 90, 255), (80, 160, 255)]),
    ("veloria", "VELORIA", [(255, 96, 40), (255, 170, 50), (255, 225, 90), (255, 80, 150)]),
    ("kryon", "KRYON", [(30, 140, 255), (70, 210, 255), (150, 245, 255), (180, 150, 255)]),
    ("nymeris", "NYMERIS", [(255, 40, 70), (255, 100, 40), (255, 170, 50), (200, 40, 170)]),
    ("solvex", "SOLVEX", [(255, 170, 0), (255, 225, 60), (255, 250, 170), (255, 150, 30)]),
    ("eclyra", "ECLYRA", [(20, 225, 235), (60, 170, 255), (150, 100, 255), (235, 100, 255)]),
    ("nexorion", "NEXORION", [(90, 90, 255), (190, 90, 255), (255, 100, 200), (255, 200, 120)]),
    ("helper", "ХЕЛПЕР", [(20, 215, 120), (80, 250, 170), (60, 220, 255), (140, 255, 200)]),
    ("junior_moder", "МЛАДШИЙ МОДЕРАТОР", [(30, 100, 255), (50, 170, 255), (80, 225, 255), (150, 245, 255)]),
    ("moder", "МОДЕРАТОР", [(120, 50, 255), (190, 90, 255), (255, 100, 220), (255, 150, 235)]),
    ("senior_moder", "СТАРШИЙ МОДЕРАТОР", [(230, 25, 90), (255, 80, 130), (255, 140, 100), (255, 200, 110)]),
    ("admin", "АДМИНИСТРАТОР", [(255, 130, 20), (255, 190, 40), (255, 235, 90), (255, 250, 200)]),
    ("staff", "ГЛАВНЫЙ АДМИНИСТРАТОР", [(255, 45, 85), (255, 120, 40), (255, 205, 50), (255, 245, 160)]),
]


def letters(label):
    return [c for c in label if c != " "]


def offsets():
    """rank id -> glyph offset from BASE (cumulative letter count)."""
    result, offset = {}, 0
    for rank_id, label, _ in RANKS:
        result[rank_id] = offset
        offset += len(letters(label))
    return result, offset


def build_all():
    offset_by_id, total = offsets()
    first_page, last_page = BASE >> 8, (BASE + total - 1) >> 8
    font_dir = ROOT / "font"
    font_dir.mkdir(parents=True, exist_ok=True)

    # The clan glyphs end right below BASE, in the same page: keep them.
    pages = {}
    for page in range(first_page, last_page + 1):
        path = font_dir / f"glyph_{page:02X}.png"
        pages[page] = Image.open(path).convert("RGBA") if path.exists() else Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))

    for rank_id, label, stops in RANKS:
        # Lay the word out first so the gradient can run over its real width.
        x, starts = 0, []
        for ch in label:
            if ch == " ":
                x += gs.SPACE
                continue
            starts.append(x)
            x += gs.used_width(ch)
        total_w = max(1, x)
        for position, ch in enumerate(letters(label)):
            x0 = starts[position]
            glyph = gs.render(ch, lambda gx, x0=x0: gs.gradient(stops, (x0 + gs.PAD + gx) / total_w))
            codepoint = BASE + offset_by_id[rank_id] + position
            slot = codepoint & 0xFF
            # paste() replaces the cell, so re-running the generator never double-draws.
            pages[codepoint >> 8].paste(glyph, ((slot % 16) * gs.CELL, (slot // 16) * gs.CELL))

    for page, sheet in pages.items():
        sheet.save(font_dir / f"glyph_{page:02X}.png", optimize=True)
    # Older builds spread the rank glyphs over later pages; drop those leftovers.
    for page in range(last_page + 1, 0xFA):
        stale = font_dir / f"glyph_{page:02X}.png"
        if stale.exists():
            stale.unlink()
    print(f"Generated {total} rank glyphs (U+{BASE:04X}..U+{BASE + total - 1:04X}).")
    for rank_id, label, _ in RANKS:
        print(f"  {rank_id:13s} offset {offset_by_id[rank_id]:3d}  {label}")


if __name__ == "__main__":
    build_all()

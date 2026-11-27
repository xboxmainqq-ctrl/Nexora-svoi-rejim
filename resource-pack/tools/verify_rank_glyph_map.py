#!/usr/bin/env python3
"""Fail if the rank glyph table in the font generator and in the two plugins disagree.

The plugins turn a rank into glyph codepoints (U+F750 + offset + letter position),
the generator draws those glyphs: if the order, labels or offsets drift apart,
players see wrong letters. Run in CI after build_pack.py.
"""
import re
import sys
from pathlib import Path

import make_rank_gradient_font as gen

REPO = Path(__file__).resolve().parents[2]
DONATE = REPO / "plugins/NEXORA_Donate/src/main/java/ru/nexora/donate/NEXORADonate.java"
STAFF = REPO / "plugins/NEXORA_AdminRanks/src/main/java/ru/nexora/adminranks/NEXORAAdminRanks.java"
PAIR = re.compile(r'\{\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\}')


def table(source, name):
    match = re.search(name + r"\s*=\s*\{(.*?)\n\s*\};", source, re.S)
    if not match:
        sys.exit(f"Cannot find {name}")
    return PAIR.findall(match.group(1))


def main():
    donate_src, staff_src = DONATE.read_text(encoding="utf-8"), STAFF.read_text(encoding="utf-8")
    donate = table(donate_src, "GLYPH_TABLE")
    staff = table(staff_src, "RANK_TABLE")
    expected = [(rank_id, label) for rank_id, label, _ in gen.RANKS]
    if donate + staff != expected:
        sys.exit(f"Rank tables differ.\n plugins:   {donate + staff}\n generator: {expected}")
    donate_letters = sum(len(label.replace(" ", "")) for _, label in donate)
    declared = int(re.search(r"STAFF_GLYPH_START\s*=\s*(\d+)", staff_src).group(1))
    if declared != donate_letters:
        sys.exit(f"STAFF_GLYPH_START is {declared}, expected {donate_letters}")
    for src, path in ((donate_src, DONATE), (staff_src, STAFF)):
        base = int(re.search(r"GLYPH_BASE\s*=\s*0x([0-9A-Fa-f]+)", src).group(1), 16)
        if base != gen.BASE:
            sys.exit(f"GLYPH_BASE in {path.name} is U+{base:04X}, generator uses U+{gen.BASE:04X}")
    print(f"Rank glyph map OK: {len(expected)} ranks, {gen.offsets()[1]} glyphs.")


if __name__ == "__main__":
    main()

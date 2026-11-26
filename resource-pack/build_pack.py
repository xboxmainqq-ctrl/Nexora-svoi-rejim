#!/usr/bin/env python3
"""Build the NEXORA BEASTS Bedrock resource pack without third-party packages."""
from pathlib import Path
import struct
import zlib
import zipfile

ROOT = Path(__file__).resolve().parent / "NEXORA_Beasts_RP"
BUILD = Path(__file__).resolve().parent / "build"
WIDTH = HEIGHT = 64

# A small hand-authored pixel-art palette for the first NEXORA wolf prototype.
INK = (24, 35, 52, 255)
FUR = (69, 91, 115, 255)
FUR_LIGHT = (117, 145, 166, 255)
FUR_DARK = (43, 60, 80, 255)
CYAN = (55, 222, 231, 255)
CYAN_DARK = (24, 126, 151, 255)
GOLD = (231, 184, 91, 255)
WHITE = (220, 239, 241, 255)


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


def write_png(path, pixels):
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def paint(pixels, x, y, w, h, color):
    for py in range(max(0, y), min(HEIGHT, y + h)):
        for px in range(max(0, x), min(WIDTH, x + w)):
            pixels[py][px] = color


def make_texture():
    pixels = [[FUR for _ in range(WIDTH)] for _ in range(HEIGHT)]

    # Subtle fur grain and alternating slate-blue bands.
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if ((x * 7 + y * 11) % 19) < 3:
                pixels[y][x] = FUR_LIGHT
            elif ((x * 3 + y * 5) % 23) < 4:
                pixels[y][x] = FUR_DARK

    # Head / muzzle patches.
    paint(pixels, 0, 0, 24, 3, INK)
    paint(pixels, 0, 3, 24, 2, FUR_LIGHT)
    paint(pixels, 0, 5, 24, 2, FUR)
    paint(pixels, 24, 0, 16, 8, FUR_DARK)
    paint(pixels, 40, 0, 8, 8, CYAN_DARK)
    paint(pixels, 42, 1, 4, 4, CYAN)
    paint(pixels, 43, 2, 2, 2, WHITE)

    # Torso: dark saddle with a glowing rune stripe.
    paint(pixels, 0, 16, 32, 12, FUR_DARK)
    paint(pixels, 2, 18, 28, 2, FUR_LIGHT)
    paint(pixels, 14, 20, 4, 8, CYAN_DARK)
    paint(pixels, 15, 21, 2, 6, CYAN)
    paint(pixels, 4, 25, 4, 2, GOLD)
    paint(pixels, 24, 25, 4, 2, GOLD)

    # Shoulder plates and leg wraps.
    for x in (32, 40, 48, 56):
        paint(pixels, x, 16, 8, 12, FUR_DARK)
        paint(pixels, x + 1, 18, 6, 2, FUR_LIGHT)
        paint(pixels, x + 2, 23, 4, 2, CYAN_DARK)
        paint(pixels, x + 3, 23, 2, 1, CYAN)

    # Tail: silver tip and a gold band.
    paint(pixels, 0, 40, 16, 8, FUR_DARK)
    paint(pixels, 0, 44, 16, 2, FUR_LIGHT)
    paint(pixels, 6, 40, 3, 8, GOLD)
    paint(pixels, 7, 40, 1, 8, CYAN)

    # Extra runic details across unused UV space.
    for x in (20, 28, 36, 44, 52):
        paint(pixels, x, 40, 3, 3, CYAN_DARK)
        paint(pixels, x + 1, 41, 1, 1, CYAN)
        paint(pixels, x, 48, 4, 2, GOLD)

    write_png(ROOT / "textures/entity/nexora_wolf.png", pixels)


def main():
    make_texture()
    BUILD.mkdir(parents=True, exist_ok=True)
    output = BUILD / "NEXORA_Beasts_RP.mcpack"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(ROOT.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(ROOT).as_posix())
    print(f"Created {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

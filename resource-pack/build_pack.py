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


def make_fox_texture():
    ink = (55, 31, 25, 255)
    orange = (205, 105, 48, 255)
    light = (244, 190, 116, 255)
    cream = (247, 226, 190, 255)
    cyan = (54, 222, 219, 255)
    gold = (239, 184, 80, 255)
    pixels = [[orange for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if ((x * 5 + y * 9) % 21) < 3:
                pixels[y][x] = light
            elif ((x * 7 + y * 3) % 29) < 3:
                pixels[y][x] = ink
    # Face, cream muzzle and dark ear tips.
    paint(pixels, 0, 0, 24, 3, ink)
    paint(pixels, 0, 3, 24, 4, light)
    paint(pixels, 24, 0, 16, 8, cream)
    paint(pixels, 40, 0, 8, 8, ink)
    paint(pixels, 42, 2, 4, 3, cyan)
    paint(pixels, 43, 3, 2, 1, gold)
    # Rust-red back with a pale chest stripe and magical shoulder mark.
    paint(pixels, 0, 16, 32, 12, orange)
    paint(pixels, 2, 18, 8, 8, cream)
    paint(pixels, 14, 19, 4, 8, cyan)
    paint(pixels, 13, 21, 6, 2, gold)
    # Four legs and dark paws.
    for x in (32, 40, 48, 56):
        paint(pixels, x, 16, 8, 12, orange)
        paint(pixels, x, 25, 8, 3, ink)
        paint(pixels, x + 2, 19, 4, 2, light)
    # Large bushy tail with a pale tip.
    paint(pixels, 0, 40, 16, 8, orange)
    paint(pixels, 0, 44, 16, 4, cream)
    paint(pixels, 5, 40, 3, 4, gold)
    for x in (20, 28, 36, 44, 52):
        paint(pixels, x, 40, 3, 3, cyan)
        paint(pixels, x + 1, 41, 1, 1, cream)
    write_png(ROOT / "textures/entity/nexora_fox.png", pixels)


def make_cat_texture():
    ink = (30, 29, 53, 255)
    fur = (78, 72, 111, 255)
    light = (139, 130, 170, 255)
    muzzle = (224, 198, 207, 255)
    cyan = (65, 235, 222, 255)
    gold = (239, 190, 91, 255)
    pixels = [[fur for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if ((x * 11 + y * 5) % 23) < 3:
                pixels[y][x] = light
            elif ((x * 3 + y * 7) % 31) < 3:
                pixels[y][x] = ink
    # Pointed ears, bright eyes and a soft muzzle.
    paint(pixels, 0, 0, 24, 4, ink)
    paint(pixels, 0, 4, 24, 4, fur)
    paint(pixels, 24, 0, 16, 8, muzzle)
    paint(pixels, 40, 0, 8, 8, cyan)
    paint(pixels, 42, 2, 4, 3, gold)
    # Sleek dark body with a luminous collar/rune.
    paint(pixels, 0, 16, 32, 12, ink)
    paint(pixels, 2, 18, 28, 3, fur)
    paint(pixels, 14, 20, 4, 7, cyan)
    paint(pixels, 12, 21, 8, 2, gold)
    # Paws and leg markings.
    for x in (32, 40, 48, 56):
        paint(pixels, x, 16, 8, 12, fur)
        paint(pixels, x, 25, 8, 3, ink)
        paint(pixels, x + 2, 19, 4, 2, light)
    # Long tail with a glowing tip.
    paint(pixels, 0, 40, 16, 8, fur)
    paint(pixels, 0, 44, 16, 3, ink)
    paint(pixels, 6, 40, 3, 4, cyan)
    for x in (20, 28, 36, 44, 52):
        paint(pixels, x, 40, 3, 3, gold)
        paint(pixels, x + 1, 41, 1, 1, cyan)
    write_png(ROOT / "textures/entity/nexora_cat.png", pixels)



def make_bee_texture():
    ink=(31,37,49,255); yellow=(246,190,55,255); pale=(255,226,112,255)
    cyan=(64,229,225,255); wing=(173,236,238,190); gold=(240,157,48,255)
    pixels=[[yellow for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x+y*2)%17<3: pixels[y][x]=pale
    # Abdomen bands, face, translucent-looking wings and luminous eyes.
    paint(pixels,0,0,24,4,ink); paint(pixels,0,4,24,4,yellow)
    paint(pixels,24,0,16,8,ink); paint(pixels,28,2,3,3,cyan); paint(pixels,36,2,3,3,cyan)
    paint(pixels,0,16,32,12,yellow); paint(pixels,0,20,32,3,ink)
    paint(pixels,8,24,16,3,ink); paint(pixels,14,17,4,10,gold)
    paint(pixels,32,16,12,8,wing); paint(pixels,46,16,12,8,wing)
    paint(pixels,0,40,16,8,ink); paint(pixels,4,41,8,5,gold)
    paint(pixels,20,40,12,5,cyan)
    write_png(ROOT / "textures/entity/nexora_bee.png", pixels)


def make_axolotl_texture():
    ink=(41,42,83,255); body=(112,105,181,255); light=(176,166,222,255)
    pink=(246,127,174,255); cyan=(75,230,224,255); gold=(246,197,104,255)
    pixels=[[body for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*3+y*7)%23<3: pixels[y][x]=light
    # Broad head, bright gills, iridescent fins and a dorsal stripe.
    paint(pixels,0,0,28,8,body); paint(pixels,0,0,28,2,ink)
    paint(pixels,28,0,12,8,cyan); paint(pixels,31,2,6,3,gold)
    paint(pixels,0,16,32,10,body); paint(pixels,12,17,8,8,cyan)
    paint(pixels,14,18,4,6,gold); paint(pixels,32,16,12,8,pink)
    paint(pixels,46,16,12,8,cyan)
    paint(pixels,0,32,16,8,pink); paint(pixels,16,32,16,8,light)
    paint(pixels,32,32,16,8,cyan); paint(pixels,48,32,12,8,gold)
    write_png(ROOT / "textures/entity/nexora_axolotl.png", pixels)


def main():
    make_texture()
    make_fox_texture()
    make_cat_texture()
    make_bee_texture()
    make_axolotl_texture()
    BUILD.mkdir(parents=True, exist_ok=True)
    output = BUILD / "NEXORA_Beasts_RP.mcpack"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(ROOT.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(ROOT).as_posix())
    print(f"Created {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

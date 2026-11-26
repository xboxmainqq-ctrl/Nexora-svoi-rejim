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



def make_horse_texture():
    ink=(48,30,39,255); coat=(133,73,59,255); light=(197,128,94,255)
    cyan=(62,222,224,255); gold=(242,190,87,255); mane=(48,38,54,255)
    pixels=[[coat for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*5+y*3)%27<3: pixels[y][x]=light
    paint(pixels,0,0,24,3,ink); paint(pixels,0,3,24,5,light)
    paint(pixels,24,0,16,8,coat); paint(pixels,28,2,3,3,cyan)
    paint(pixels,36,2,3,3,gold); paint(pixels,0,16,32,12,coat)
    paint(pixels,12,17,8,10,mane); paint(pixels,14,18,4,8,gold)
    paint(pixels,32,16,24,12,coat)
    for x in (32,40,48,56):
        paint(pixels,x,18,8,8,light); paint(pixels,x,25,8,3,ink)
    paint(pixels,0,40,16,8,mane); paint(pixels,4,40,8,8,gold)
    paint(pixels,20,40,12,5,cyan)
    write_png(ROOT / "textures/entity/nexora_horse.png", pixels)


def make_panda_texture():
    ink=(27,32,43,255); white=(235,239,231,255); black=(42,43,55,255)
    cyan=(55,222,220,255); gold=(235,187,89,255); grey=(158,174,184,255)
    pixels=[[white for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*7+y*11)%31<3: pixels[y][x]=grey
    paint(pixels,0,0,24,8,white); paint(pixels,0,0,7,8,black)
    paint(pixels,17,0,7,8,black); paint(pixels,24,0,16,8,white)
    paint(pixels,28,2,4,3,cyan); paint(pixels,36,2,4,3,gold)
    paint(pixels,0,16,32,12,white); paint(pixels,4,17,8,10,black)
    paint(pixels,20,17,8,10,black); paint(pixels,14,19,4,8,cyan)
    paint(pixels,32,16,24,12,black)
    for x in (32,40,48,56):
        paint(pixels,x,16,8,12,black); paint(pixels,x+2,18,4,3,white)
    paint(pixels,0,40,16,8,black); paint(pixels,4,41,8,4,gold)
    paint(pixels,20,40,12,5,cyan)
    write_png(ROOT / "textures/entity/nexora_panda.png", pixels)


def make_polar_bear_texture():
    ink=(42,62,91,255); ice=(213,240,247,255); snow=(246,253,255,255)
    blue=(117,195,221,255); cyan=(55,224,230,255); gold=(236,196,111,255)
    pixels=[[ice for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*3+y*5)%25<3: pixels[y][x]=snow
    paint(pixels,0,0,24,3,ink); paint(pixels,0,3,24,5,snow)
    paint(pixels,24,0,16,8,ice); paint(pixels,28,2,3,3,cyan)
    paint(pixels,36,2,3,3,gold); paint(pixels,0,16,32,12,ice)
    paint(pixels,12,17,8,10,blue); paint(pixels,14,18,4,8,cyan)
    paint(pixels,32,16,24,12,ice)
    for x in (32,40,48,56):
        paint(pixels,x,16,8,12,ice); paint(pixels,x,25,8,3,blue)
    paint(pixels,0,40,16,8,blue); paint(pixels,4,40,8,8,cyan)
    paint(pixels,20,40,12,5,gold)
    write_png(ROOT / "textures/entity/nexora_polar_bear.png", pixels)



def make_rabbit_texture():
    ink=(48,38,63,255); fur=(177,157,197,255); light=(230,215,235,255)
    pink=(245,142,183,255); cyan=(62,224,222,255); gold=(238,190,98,255)
    pixels=[[fur for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*5+y*7)%23<3: pixels[y][x]=light
    paint(pixels,0,0,24,4,ink); paint(pixels,0,4,24,4,light)
    paint(pixels,24,0,16,8,fur); paint(pixels,28,2,3,3,cyan)
    paint(pixels,36,2,3,3,gold); paint(pixels,0,16,32,12,fur)
    paint(pixels,6,16,5,12,pink); paint(pixels,21,16,5,12,pink)
    paint(pixels,14,18,4,8,cyan); paint(pixels,32,16,24,12,fur)
    for x in (32,40,48,56): paint(pixels,x,24,8,4,ink)
    paint(pixels,0,40,16,8,light); paint(pixels,4,41,8,5,pink)
    paint(pixels,20,40,12,5,gold)
    write_png(ROOT / "textures/entity/nexora_rabbit.png", pixels)


def make_ocelot_texture():
    ink=(42,35,48,255); coat=(218,164,91,255); cream=(247,220,168,255)
    spots=(91,58,49,255); cyan=(60,222,222,255); gold=(239,185,83,255)
    pixels=[[coat for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*7+y*13)%17<2: pixels[y][x]=spots
    paint(pixels,0,0,24,3,ink); paint(pixels,0,3,24,5,cream)
    paint(pixels,24,0,16,8,coat); paint(pixels,28,2,3,3,cyan)
    paint(pixels,36,2,3,3,gold); paint(pixels,0,16,32,12,coat)
    for x,y in [(3,18),(10,22),(21,18),(26,24),(14,25)]: paint(pixels,x,y,3,3,spots)
    paint(pixels,14,18,4,8,cyan); paint(pixels,32,16,24,12,coat)
    for x in (32,40,48,56): paint(pixels,x+2,19,3,3,spots)
    paint(pixels,0,40,16,8,ink); paint(pixels,4,41,8,4,gold)
    write_png(ROOT / "textures/entity/nexora_ocelot.png", pixels)


def make_turtle_texture():
    ink=(34,66,69,255); shell=(65,132,105,255); shell_light=(122,188,137,255)
    skin=(111,191,166,255); cyan=(58,224,220,255); gold=(237,194,101,255)
    pixels=[[skin for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*3+y*5)%19<3: pixels[y][x]=shell_light
    paint(pixels,0,0,24,4,ink); paint(pixels,0,4,24,4,skin)
    paint(pixels,24,0,16,8,skin); paint(pixels,28,2,3,3,cyan)
    paint(pixels,36,2,3,3,gold); paint(pixels,0,16,32,12,shell)
    paint(pixels,4,17,24,2,shell_light); paint(pixels,14,19,4,8,cyan)
    paint(pixels,32,16,24,12,skin)
    for x in (32,40,48,56): paint(pixels,x,18,8,8,shell)
    paint(pixels,0,40,16,8,shell); paint(pixels,4,41,8,5,gold)
    write_png(ROOT / "textures/entity/nexora_turtle.png", pixels)


def make_dolphin_texture():
    ink=(25,55,83,255); blue=(68,139,177,255); light=(155,215,225,255)
    cyan=(53,230,225,255); gold=(240,192,93,255); white=(225,247,245,255)
    pixels=[[blue for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*7+y*3)%21<3: pixels[y][x]=light
    paint(pixels,0,0,24,4,ink); paint(pixels,0,4,24,4,light)
    paint(pixels,24,0,16,8,blue); paint(pixels,28,2,3,3,cyan)
    paint(pixels,36,2,3,3,gold); paint(pixels,0,16,32,12,blue)
    paint(pixels,4,18,24,2,light); paint(pixels,14,19,4,8,cyan)
    paint(pixels,32,16,24,12,blue); paint(pixels,36,18,12,5,white)
    paint(pixels,0,40,16,8,ink); paint(pixels,4,41,8,5,cyan)
    paint(pixels,20,40,12,5,gold)
    write_png(ROOT / "textures/entity/nexora_dolphin.png", pixels)


def make_parrot_texture():
    ink=(38,35,73,255); violet=(130,86,184,255); blue=(55,174,218,255)
    cyan=(59,230,218,255); gold=(248,194,83,255); red=(230,94,107,255)
    pixels=[[violet for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x*5+y*11)%19<3: pixels[y][x]=blue
    paint(pixels,0,0,24,8,ink); paint(pixels,4,2,16,4,cyan)
    paint(pixels,24,0,16,8,gold); paint(pixels,28,2,4,3,red)
    paint(pixels,0,16,32,12,violet); paint(pixels,4,17,6,10,red)
    paint(pixels,22,17,6,10,cyan); paint(pixels,14,18,4,8,gold)
    paint(pixels,32,16,24,12,blue)
    paint(pixels,34,18,8,8,red); paint(pixels,46,18,8,8,cyan)
    paint(pixels,0,40,16,8,ink); paint(pixels,4,41,8,5,gold)
    paint(pixels,20,40,12,5,cyan)
    write_png(ROOT / "textures/entity/nexora_parrot.png", pixels)



# Per-letter rarity font for Bedrock name tags.
# Every rarity label gets its own PUA glyph sequence so each character can
# carry a different baked-in color without animation or § formatting codes.
RARITY_LABELS = [
    "ОБЫЧНОЕ", "НЕОБЫЧНОЕ", "РЕДКОЕ", "ЭПИЧЕСКОЕ", "ЛЕГЕНДАРНОЕ",
    "МИФИЧЕСКОЕ", "ДРЕВНЕЕ", "БОЖЕСТВЕННОЕ", "ЗАПРЕТНОЕ", "АБСОЛЮТНОЕ",
]

# Compact 5x7 bitmap alphabet. Cyrillic forms are drawn in a blocky style
# that remains readable above a pet at normal Bedrock name-tag scale.
FONT_5X7 = {
    "А": ["01110","10001","10001","11111","10001","10001","10001"],
    "Б": ["11111","10000","10000","11110","10001","10001","11110"],
    "В": ["11110","10001","10001","11110","10001","10001","11110"],
    "Г": ["11111","10000","10000","10000","10000","10000","10000"],
    "Д": ["00110","01010","01010","01010","10001","11111","10001"],
    "Е": ["11111","10000","10000","11110","10000","10000","11111"],
    "Ж": ["10101","10101","01110","00100","01110","10101","10101"],
    "З": ["11110","00001","00001","01110","00001","00001","11110"],
    "И": ["10001","10011","10101","10101","11001","10001","10001"],
    "К": ["10001","10010","10100","11000","10100","10010","10001"],
    "Л": ["00111","01001","01001","01001","10001","10001","10001"],
    "М": ["10001","11011","10101","10101","10001","10001","10001"],
    "Н": ["10001","10001","10001","11111","10001","10001","10001"],
    "О": ["01110","10001","10001","10001","10001","10001","01110"],
    "П": ["11111","10001","10001","10001","10001","10001","10001"],
    "Р": ["11110","10001","10001","11110","10000","10000","10000"],
    "С": ["01111","10000","10000","10000","10000","10000","01111"],
    "Т": ["11111","00100","00100","00100","00100","00100","00100"],
    "У": ["10001","10001","10001","01111","00001","00010","11100"],
    "Ф": ["00100","01110","10101","10101","10101","01110","00100"],
    "Ы": ["10001","10001","10001","11101","10011","10011","11101"],
    "Ч": ["10001","10001","10001","01111","00001","00001","00001"],
    "Э": ["01110","10001","00001","00111","00001","10001","01110"],
    "Ю": ["10010","10101","10101","11101","10101","10101","10010"],
    "Я": ["01111","10001","10001","01111","00101","01001","10001"],
}

def make_rarity_font():
    """Write glyph_E2.png (U+E200+) with a static yellow-to-orange gradient."""
    size = 256
    cell = 16
    pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]
    total = sum(len(label) for label in RARITY_LABELS)
    glyph_index = 0

    def blend(start, end, amount):
        return tuple(round(start[i] + (end[i] - start[i]) * amount) for i in range(3))

    yellow = (255, 231, 92)
    orange = (255, 132, 35)
    shadow = (83, 43, 20, 180)

    for label in RARITY_LABELS:
        for position, char in enumerate(label):
            pattern = FONT_5X7[char]
            t = position / max(1, len(label) - 1)
            rgb = blend(yellow, orange, t)
            col = glyph_index % 16
            row = glyph_index // 16
            ox = col * cell + 3
            oy = row * cell + 1

            # Two-pixel pixel-art scaling with a subtle one-pixel shadow.
            for py, line in enumerate(pattern):
                for px, bit in enumerate(line):
                    if bit != "1":
                        continue
                    x = ox + px * 2
                    y = oy + py * 2
                    for sy in range(2):
                        for sx in range(2):
                            if 0 <= x + sx + 1 < size and 0 <= y + sy + 1 < size:
                                pixels[y + sy + 1][x + sx + 1] = shadow
                            pixels[y + sy][x + sx] = (*rgb, 255)
            glyph_index += 1

    # PNG uses a 16x16 grid of 16px cells, matching Bedrock glyph sheets.
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))
    path = ROOT / "font/glyph_E2.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(f"Created {path} with {glyph_index}/{total} static gradient glyphs")


def main():
    make_texture()
    make_fox_texture()
    make_cat_texture()
    make_bee_texture()
    make_axolotl_texture()
    make_horse_texture()
    make_panda_texture()
    make_polar_bear_texture()
    make_rabbit_texture()
    make_ocelot_texture()
    make_turtle_texture()
    make_dolphin_texture()
    make_parrot_texture()
    make_rarity_font()
    BUILD.mkdir(parents=True, exist_ok=True)
    output = BUILD / "NEXORA_Beasts_RP.mcpack"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(ROOT.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(ROOT).as_posix())
    print(f"Created {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

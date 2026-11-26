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



# Species palettes and UV-aware texture painter. The previous prototype painted
# arbitrary rectangles that did not match each model's actual cube UV layout.
SPECIES_PALETTES = {
    "wolf": ((87, 105, 126, 255), (151, 171, 190, 255), (37, 51, 69, 255), (64, 218, 215, 255), (226, 232, 235, 255)),
    "fox": ((190, 91, 43, 255), (244, 178, 105, 255), (76, 39, 31, 255), (255, 205, 103, 255), (250, 229, 193, 255)),
    "cat": ((79, 72, 104, 255), (164, 151, 181, 255), (35, 31, 52, 255), (88, 225, 218, 255), (226, 207, 218, 255)),
    "bee": ((232, 177, 47, 255), (255, 222, 111, 255), (38, 43, 57, 255), (75, 221, 218, 255), (255, 241, 190, 255)),
    "axolotl": ((151, 125, 190, 255), (219, 184, 218, 255), (62, 52, 100, 255), (83, 225, 224, 255), (255, 151, 190, 255)),
    "horse": ((127, 73, 55, 255), (202, 139, 103, 255), (49, 36, 43, 255), (241, 188, 85, 255), (229, 207, 177, 255)),
    "panda": ((225, 231, 225, 255), (255, 255, 246, 255), (37, 42, 48, 255), (86, 194, 145, 255), (177, 190, 187, 255)),
    "polar_bear": ((211, 231, 241, 255), (250, 255, 255, 255), (74, 108, 135, 255), (80, 207, 229, 255), (166, 218, 235, 255)),
    "rabbit": ((172, 153, 187, 255), (231, 216, 233, 255), (67, 54, 83, 255), (244, 143, 184, 255), (248, 233, 238, 255)),
    "ocelot": ((210, 157, 82, 255), (250, 219, 158, 255), (82, 52, 42, 255), (73, 207, 193, 255), (245, 229, 194, 255)),
    "turtle": ((91, 151, 112, 255), (157, 205, 151, 255), (34, 72, 64, 255), (62, 220, 208, 255), (202, 229, 174, 255)),
    "dolphin": ((73, 137, 171, 255), (177, 222, 230, 255), (34, 70, 98, 255), (73, 222, 222, 255), (235, 249, 246, 255)),
    "parrot": ((127, 85, 173, 255), (192, 145, 219, 255), (48, 39, 82, 255), (67, 218, 211, 255), (245, 193, 91, 255)),
}


def _rect(pixels, x, y, w, h, color):
    x0, y0 = int(round(x)), int(round(y))
    x1, y1 = int(round(x + w)), int(round(y + h))
    for py in range(max(0, y0), min(HEIGHT, y1)):
        for px in range(max(0, x0), min(WIDTH, x1)):
            pixels[py][px] = color


def _shade(color, factor):
    return tuple(max(0, min(255, int(channel * factor))) for channel in color[:3]) + (color[3],)


def make_entity_texture(species):
    """Paint each cuboid into its real Bedrock UV net, with species details."""
    model_path = ROOT / "models/entity" / f"nexora_{species}.geo.json"
    model = json.loads(model_path.read_text(encoding="utf-8"))
    geometry = model["minecraft:geometry"][0]
    base, light, dark, accent, cream = SPECIES_PALETTES[species]
    pixels = [[(0, 0, 0, 0) for _ in range(WIDTH)] for _ in range(HEIGHT)]

    for bone in geometry["bones"]:
        bone_name = bone["name"].lower()
        for cube_index, cube in enumerate(bone.get("cubes", [])):
            uv = cube.get("uv")
            if not isinstance(uv, list) or len(uv) != 2:
                continue
            u, v = uv
            w, h, d = cube["size"]
            # Standard Bedrock box UV net: left/front/right/back + top/bottom.
            faces = [
                ("left", u, v + d, d, h, 0.78),
                ("front", u + d, v + d, w, h, 1.00),
                ("right", u + d + w, v + d, d, h, 0.86),
                ("back", u + 2 * d + w, v + d, w, h, 0.68),
                ("top", u + d, v, w, d, 1.12),
                ("bottom", u + d + w, v, w, d, 0.62),
            ]
            for face, fx, fy, fw, fh, factor in faces:
                color = _shade(light if face == "top" else base, factor)
                if "shell" in bone_name:
                    color = _shade((116, 174, 119, 255) if face == "top" else base, factor)
                if "mane" in bone_name or "leg" in bone_name and species == "horse":
                    color = _shade(dark, 1.08 if face == "top" else 0.9)
                if "wing" in bone_name or "fin" in bone_name or "gill" in bone_name:
                    color = _shade(accent if "gill" in bone_name else light, factor)
                if "scarf" in bone_name:
                    color = _shade(accent, factor)
                if "ear" in bone_name:
                    color = _shade(accent if species in ("rabbit", "cat", "fox") else dark, factor)
                if "tail" in bone_name:
                    color = _shade(light if face == "top" else base, factor)
                _rect(pixels, fx, fy, fw, fh, color)

                # Fur grain and species markings are painted only inside mapped faces.
                x0, y0 = int(round(fx)), int(round(fy))
                x1, y1 = int(round(fx + fw)), int(round(fy + fh))
                for py in range(max(0, y0 + 1), min(HEIGHT, y1 - 1)):
                    for px in range(max(0, x0 + 1), min(WIDTH, x1 - 1)):
                        seed = (px * 17 + py * 29 + len(bone_name) * 7 + cube_index * 13)
                        if species == "ocelot" and seed % 17 < 3 and "body" in bone_name:
                            pixels[py][px] = dark
                        elif species == "bee" and "body" in bone_name and (py // 2) % 3 == 1:
                            pixels[py][px] = dark
                        elif species == "panda" and ("leg" in bone_name or "body" in bone_name and cube_index == 0):
                            if seed % 23 < 4:
                                pixels[py][px] = dark
                        elif species == "parrot" and (px + py) % 7 < 2:
                            pixels[py][px] = _shade(accent, 0.9)
                        elif seed % 31 < 2:
                            pixels[py][px] = light

                # Dark seam and a fine highlight make cube edges read cleanly in-game.
                _rect(pixels, fx, fy, fw, 1, _shade(dark, 0.9))
                _rect(pixels, fx, fy + fh - 1, fw, 1, _shade(dark, 0.82))

                # Face details on the first head cube: eyes, muzzle and nose.
                if bone_name == "head" and cube_index == 0 and face == "front" and fw >= 4 and fh >= 4:
                    eye_y = fy + max(1, round(fh * 0.34))
                    eye_left = fx + max(1, round(fw * 0.22))
                    eye_right = fx + min(round(fw - 2), round(fw * 0.78))
                    for eye_x in (eye_left, eye_right):
                        _rect(pixels, eye_x, eye_y, 1, 1, dark)
                        if eye_y + 1 < fy + fh:
                            _rect(pixels, eye_x, eye_y + 1, 1, 1, accent)
                    muzzle_y = fy + max(2, round(fh * 0.64))
                    _rect(pixels, fx + max(1, round(fw * 0.28)), muzzle_y, max(1, round(fw * 0.44)), max(1, round(fh * 0.22)), cream)
                    _rect(pixels, fx + round(fw / 2), muzzle_y, 1, 1, dark)

                # Tail tips, paws, gill edges and decorative species markings.
                if "leg" in bone_name and fh >= 3:
                    _rect(pixels, fx, fy + fh - 2, fw, 2, dark)
                if "tail" in bone_name and fw >= 2 and fh >= 2:
                    _rect(pixels, fx + max(0, round(fw * 0.35)), fy + max(0, round(fh * 0.35)), max(1, round(fw * 0.3)), max(1, round(fh * 0.3)), accent)
                if "gill" in bone_name or "fin" in bone_name or "wing" in bone_name:
                    _rect(pixels, fx, fy, fw, 1, cream)
                if "shell" in bone_name and face == "top":
                    for sy in range(int(fy) + 1, int(fy + fh) - 1, 3):
                        _rect(pixels, fx + 1, sy, max(1, fw - 2), 1, _shade(dark, 0.72))

    write_png(ROOT / "textures/entity" / f"nexora_{species}.png", pixels)


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

    # Each rarity has its own two-tone identity; legendary keeps the
    # requested yellow-to-orange gradient while the other ranks are distinct.
    rarity_gradients = [
        ((205, 216, 226), (105, 121, 141)),  # common: steel
        ((171, 246, 139), (36, 157, 91)),    # uncommon: green
        ((120, 232, 255), (37, 112, 218)),   # rare: blue
        ((222, 163, 255), (117, 58, 196)),   # epic: violet
        ((255, 239, 112), (255, 132, 35)),   # legendary: yellow -> orange
        ((255, 157, 218), (171, 49, 190)),   # mythic: pink -> magenta
        ((143, 246, 218), (20, 119, 128)),   # ancient: jade
        ((255, 251, 207), (205, 160, 66)),   # divine: pearl -> gold
        ((255, 132, 132), (137, 24, 45)),    # forbidden: crimson
        ((255, 255, 255), (126, 111, 229)),  # absolute: white -> astral violet
    ]
    shadow = (29, 24, 38, 220)

    for rarity_index, label in enumerate(RARITY_LABELS):
        start_color, end_color = rarity_gradients[rarity_index]
        for position, char in enumerate(label):
            pattern = FONT_5X7[char]
            t = position / max(1, len(label) - 1)
            rgb = blend(start_color, end_color, t)
            col = glyph_index % 16
            row = glyph_index // 16
            # Compact 5x7 lettering instead of the oversized 10x14 version.
            ox = col * cell + 5
            oy = row * cell + 4

            for py, line in enumerate(pattern):
                for px, bit in enumerate(line):
                    if bit != "1":
                        continue
                    x = ox + px
                    y = oy + py
                    if 0 <= x + 1 < size and 0 <= y + 1 < size:
                        pixels[y + 1][x + 1] = shadow
                    pixels[y][x] = (*rgb, 255)
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
    for species in (
        "wolf", "fox", "cat", "bee", "axolotl", "horse", "panda",
        "polar_bear", "rabbit", "ocelot", "turtle", "dolphin", "parrot",
    ):
        make_entity_texture(species)
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

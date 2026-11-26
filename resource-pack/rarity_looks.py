#!/usr/bin/env python3
"""NEXORA BEASTS — ten rarity looks (models + textures + client-entity wiring).

Each rarity has its own creature, modelled from real Bedrock cubes and painted
procedurally (no external art assets):

  common    grey wolf                 uncommon  moss turtle
  rare      crystal-blue wolf         epic      violet striped tiger
  legendary golden maned lion         mythic    pink crystal kitsune
  ancient   teal antlered stag        divine    white winged angel wolf
  forbidden abyss spiked dragon-wolf  absolute  astral dragon-winged wolf

Looks are delivered through the *existing* custom client entities
(wolf / turtle / ocelot / fox / horse): the server sets the entity variant
(query.variant) and the render controller picks the matching geometry and
texture.  Variant 0 is always the original species look.
"""
from pathlib import Path
import json
import math

TEX = 128

# ---------------------------------------------------------------- helpers

def _hash(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def _mul(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (255,)


def _mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def _b(bones, name):
    return next(b for b in bones if b["name"] == name)


def cube(origin, size, tag):
    return {"origin": list(origin), "size": list(size), "tag": tag}


def bone(name, pivot, cubes, parent=None, rotation=None):
    b = {"name": name, "pivot": list(pivot), "cubes": cubes}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = list(rotation)
    return b


# ------------------------------------------------------- quadruped builder

def quadruped(body, head, snout, leg_h, leg_w, ear, tail_len, head_dy=0.0,
              ear_tag="ear", tail_tags=None):
    """Return bones body/head/leg0-3/tail for a generic four-legged beast.

    Coordinates are Bedrock units, model front is -Z.
    body=(w,h,l)  head=(w,h,d)  snout=(w,h,d)  ear=(w,h,d)
    """
    bw, bh, bl = body
    hw, hh, hd = head
    sw, sh, sd = snout
    ew, eh, ed = ear
    top = leg_h + bh
    bones = [bone("body", [0, leg_h + bh / 2, 0],
                  [cube([-bw / 2, leg_h, -bl / 2], [bw, bh, bl], "body")])]

    hy = top - hh + 1 + head_dy
    hz = -bl / 2 - hd + 2
    head_cubes = [
        cube([-hw / 2, hy, hz], [hw, hh, hd], "head"),
        cube([-sw / 2, hy, hz - sd], [sw, sh, sd], "snout"),
        cube([-1, hy + sh - 1, hz - sd - 1], [2, 1, 1], "nose"),
    ]
    ear_z = hz + hd - ed - 1
    ears = [
        bone("ear_l", [hw / 2 - ew / 2, hy + hh, ear_z + ed / 2],
             [cube([hw / 2 - ew, hy + hh, ear_z], [ew, eh, ed], ear_tag)], parent="head"),
        bone("ear_r", [-hw / 2 + ew / 2, hy + hh, ear_z + ed / 2],
             [cube([-hw / 2, hy + hh, ear_z], [ew, eh, ed], ear_tag)], parent="head"),
    ]
    bones.append(bone("head", [0, hy + hh / 2, -bl / 2], head_cubes))

    xs = bw / 2 - leg_w / 2
    zf = -bl / 2 + leg_w
    zb = bl / 2 - leg_w
    for name, x, z in (("leg0", -xs, zf), ("leg1", xs, zf), ("leg2", -xs, zb), ("leg3", xs, zb)):
        bones.append(bone(name, [x, leg_h, z], [
            cube([x - leg_w / 2, 0, z - leg_w / 2], [leg_w, leg_h, leg_w], "leg"),
            cube([x - leg_w / 2, 0, z - leg_w / 2], [leg_w, 1, leg_w], "paw"),
        ]))

    tail_tags = tail_tags or ["tail"] * 3
    seg = max(2, int(tail_len // 3))
    tc = []
    for i, tag in enumerate(tail_tags):
        tc.append(cube([-1, top - 3 + i * 0.0 - i, bl / 2 + i * seg], [2, 2, seg], tag))
    bones.append(bone("tail", [0, top - 2, bl / 2], tc))
    return bones + ears


def feather_wing(side, root, lens, tag="wing", bone_tag="wing_bone", drop=1.0, step=2):
    """A layered feathered wing.  side=+1 left (+X), -1 right (-X)."""
    sx, sy, sz = root
    cubes = [cube([sx if side > 0 else sx - 2, sy, sz], [2, 2, 2], bone_tag)]
    for i, ln in enumerate(lens):
        ox = sx + 2 if side > 0 else sx - 2 - ln
        cubes.append(cube([ox, sy + 1 - i * drop, sz + i * step * 0.5], [ln, 1, step], tag))
    # leading-edge bone running outward along the top
    ln0 = lens[0]
    cubes.append(cube([sx + 2 if side > 0 else sx - 2 - ln0, sy + 2, sz], [ln0, 1, 1], bone_tag))
    return cubes


def dragon_wing(side, root, span, tag="wing", bone_tag="wing_bone"):
    """Bat-style membrane wing: three finger bones with membrane panels."""
    sx, sy, sz = root
    cubes = [cube([sx if side > 0 else sx - 2, sy, sz], [2, 2, 2], bone_tag)]
    fingers = [(span, 3), (span - 3, 1), (span - 6, -1)]
    for i, (ln, dz) in enumerate(fingers):
        ox = sx + 2 if side > 0 else sx - 2 - ln
        cubes.append(cube([ox, sy + 3 - i * 2, sz + dz + i], [ln, 1, 1], bone_tag))
        if i < 2:
            nxt = fingers[i + 1][0]
            pl = min(ln, nxt)
            pox = sx + 2 if side > 0 else sx - 2 - pl
            cubes.append(cube([pox, sy + 2 - i * 2, sz + dz + i], [pl, 1, 3], tag))
    return cubes


# --------------------------------------------------------------- the looks

def _wolf_base(**kw):
    p = dict(body=(6, 6, 9), head=(6, 6, 5), snout=(3, 3, 3), leg_h=7, leg_w=2,
             ear=(2, 2, 1), tail_len=7)
    p.update(kw)
    return quadruped(**p)


def look_common():
    return _wolf_base()


def look_rare():
    bones = _wolf_base(ear_tag="crystal", tail_tags=["tail", "tail", "crystal"])
    body = bones[0]
    for i, z in enumerate((-3, 0, 3)):
        body["cubes"].append(cube([-1, 13, z], [2, 2 + (i == 1), 2], "crystal"))
    bones[1]["cubes"].append(cube([-2, 8, -8], [4, 2, 2], "crystal"))   # chest/collar gem
    return bones


def look_uncommon():
    shell = [cube([-5, 5, -6], [10, 5, 12], "shell"),
             cube([-4, 10, -5], [8, 2, 10], "shell_top"),
             cube([-2, 12, -3], [4, 1, 6], "moss"),
             cube([-1, 11, -6], [2, 2, 2], "crystal"),
             cube([-1, 11, 4], [2, 2, 2], "crystal")]
    bones = [
        bone("body", [0, 5, 0], [cube([-4, 3, -5], [8, 3, 10], "body")]),
        bone("shell", [0, 6, 0], shell),
        bone("head", [0, 5, -6], [cube([-2.5, 3, -11], [5, 4, 5], "head"),
                                  cube([-2, 3, -12], [4, 2, 1], "snout")]),
        bone("flipper0", [-5, 4, -3], [cube([-9, 3, -5], [4, 1, 4], "leg")]),
        bone("flipper1", [5, 4, -3], [cube([5, 3, -5], [4, 1, 4], "leg")]),
        bone("flipper2", [-4, 4, 4], [cube([-7, 2, 3], [3, 2, 3], "leg")]),
        bone("flipper3", [4, 4, 4], [cube([4, 2, 3], [3, 2, 3], "leg")]),
        bone("tail", [0, 4, 6], [cube([-1, 3, 6], [2, 1, 3], "tail")]),
    ]
    return bones


def look_epic_tiger():
    bones = quadruped(body=(5, 5, 11), head=(5, 5, 5), snout=(3, 2, 2), leg_h=6, leg_w=2,
                      ear=(2, 2, 1), tail_len=11,
                      tail_tags=["tail", "stripe", "tail", "stripe", "tail_tip"][:3])
    # long ringed tail curling up
    tail = _b(bones, "tail")
    tail["cubes"] = [cube([-1, 9, 5.5 + i * 2], [2, 2, 2], "stripe" if i % 2 else "tail") for i in range(5)]
    tail["cubes"].append(cube([-1, 10, 15.5], [2, 3, 2], "tail_tip"))
    bones[0]["cubes"].append(cube([-2, 11, -4], [4, 1, 7], "stripe"))  # dorsal stripe ridge
    return bones


def look_legendary_lion():
    bones = quadruped(body=(6, 6, 11), head=(5, 5, 4), snout=(3, 2, 2), leg_h=7, leg_w=3,
                      ear=(2, 2, 1), tail_len=9, ear_tag="ear")
    head = bones[1]
    # big layered mane
    head["cubes"] += [
        cube([-5, 8, -5], [10, 9, 4], "mane"),
        cube([-4, 17, -5], [8, 1, 4], "mane_dark"),
        cube([-6, 9, -4], [1, 6, 3], "mane_dark"),
        cube([5, 9, -4], [1, 6, 3], "mane_dark"),
        cube([-4, 6, -5], [8, 2, 3], "mane_dark"),
    ]
    tail = _b(bones, "tail")
    tail["cubes"] = [cube([-1, 9, 5.5], [2, 2, 5], "tail"), cube([-1.5, 8, 10.5], [3, 4, 3], "mane")]
    return bones


def look_mythic_kitsune():
    bones = quadruped(body=(5, 5, 9), head=(5, 5, 5), snout=(3, 2, 3), leg_h=6, leg_w=2,
                      ear=(2, 3, 1), tail_len=8, ear_tag="ear",
                      tail_tags=["tail", "tail", "tail_tip"])
    # three feathered crystal "wings" per side (nine-tail style) + crystal tails
    for side, name in ((1, "wing_l"), (-1, "wing_r")):
        cubes = []
        for i, (dy, ln) in enumerate(((0, 6), (-2, 7), (-4, 5))):
            ox = 3 if side > 0 else -3 - ln
            cubes.append(cube([ox, 12 + dy, -1 + i * 2], [ln, 1, 3], "crystal_wing"))
        cubes.append(cube([2 if side > 0 else -4, 10, -1], [2, 3, 2], "crystal"))
        bones.append(bone(name, [side * 2, 11, 0], cubes, parent="body"))
    bones.append(bone("tail2", [0, 9, 5], [cube([-1, 10, 5], [2, 3, 5], "crystal"),
                                           cube([-1, 12, 9], [2, 3, 3], "crystal_wing")], parent="body"))
    return bones


def look_ancient_stag():
    bones = [
        bone("body", [0, 12, 0], [cube([-3, 9, -6], [6, 6, 12], "body")]),
        bone("head", [0, 21, -7], [
            cube([-2.5, 17, -11], [5, 5, 5], "head"),
            cube([-1.5, 17, -14], [3, 3, 3], "snout"),
            cube([-1, 18, -15], [2, 1, 1], "nose"),
            # antlers: trunks + tines
            cube([-3, 22, -9], [1, 5, 1], "antler"), cube([2, 22, -9], [1, 5, 1], "antler"),
            cube([-5, 25, -9], [2, 1, 1], "antler"), cube([3, 25, -9], [2, 1, 1], "antler"),
            cube([-5, 26, -9], [1, 4, 1], "antler_glow"), cube([4, 26, -9], [1, 4, 1], "antler_glow"),
            cube([-3, 27, -9], [1, 4, 1], "antler_glow"), cube([2, 27, -9], [1, 4, 1], "antler_glow"),
            cube([-4, 28, -9], [1, 2, 1], "antler_glow"), cube([3, 28, -9], [1, 2, 1], "antler_glow"),
        ]),
        bone("ear_l", [2, 22, -7], [cube([0.5, 22, -7.5], [2, 3, 1], "ear")], parent="head"),
        bone("ear_r", [-2, 22, -7], [cube([-2.5, 22, -7.5], [2, 3, 1], "ear")], parent="head"),
        bone("neck", [0, 15, -6], [cube([-1.5, 15, -9], [3, 7, 3], "body")], parent="body"),
    ]
    xs, zf, zb = 2, -4, 4
    for name, x, z in (("leg0", -xs, zf), ("leg1", xs, zf), ("leg2", -xs, zb), ("leg3", xs, zb)):
        bones.append(bone(name, [x, 9, z], [cube([x - 1, 0, z - 1], [2, 9, 2], "leg"),
                                            cube([x - 1, 0, z - 1], [2, 1, 2], "paw")]))
    bones.append(bone("tail", [0, 14, 6], [cube([-1, 12, 6], [2, 3, 2], "tail_tip")]))
    return bones


def look_divine_angel():
    bones = _wolf_base(ear_tag="ear", tail_tags=["tail", "tail", "tail_tip"])
    head = bones[1]
    head["cubes"] += [
        cube([-2.5, 14, -7], [1, 3, 1], "horn"), cube([1.5, 14, -7], [1, 3, 1], "horn"),
        cube([-2.5, 16, -6.5], [1, 1, 1], "horn"), cube([1.5, 16, -6.5], [1, 1, 1], "horn"),
        # halo
        cube([-3, 19, -8], [6, 1, 1], "halo"), cube([-3, 19, -3], [6, 1, 1], "halo"),
        cube([-3, 19, -7], [1, 1, 4], "halo"), cube([2, 19, -7], [1, 1, 4], "halo"),
    ]
    for side, name in ((1, "wing_l"), (-1, "wing_r")):
        bones.append(bone(name, [side * 3, 12, -1],
                          feather_wing(side, (side * 3, 11, -1), [10, 9, 8, 6, 4], drop=1.2),
                          parent="body"))
    for b in [x for x in bones if x["name"].startswith("leg")]:
        b["cubes"].append(cube([b["cubes"][0]["origin"][0] - 0.5, 3, b["cubes"][0]["origin"][2] - 0.5],
                               [3, 1, 3], "gold"))
    return bones


def look_forbidden_abyss():
    bones = _wolf_base(body=(7, 7, 10), head=(7, 6, 5), snout=(4, 3, 4), leg_h=7, leg_w=3,
                       ear=(2, 3, 1), tail_len=9, ear_tag="ear",
                       tail_tags=["tail", "spike", "spike"])
    body = bones[0]
    for i in range(5):
        body["cubes"].append(cube([-1, 14, -4 + i * 2], [2, 3 + (i % 2) * 2, 2], "spike"))
    for sx in (-4, 3):
        for i in range(3):
            body["cubes"].append(cube([sx, 12, -3 + i * 3], [1, 3, 1], "spike"))
    bones[1]["cubes"] += [cube([-3.5, 12, -9], [1, 3, 1], "spike"), cube([2.5, 12, -9], [1, 3, 1], "spike"),
                          cube([-1.5, 9, -14], [1, 1, 1], "fang"), cube([0.5, 9, -14], [1, 1, 1], "fang")]
    return bones


def look_absolute_dragon():
    bones = _wolf_base(ear_tag="crest", tail_tags=["tail", "tail", "tail_tip"])
    bones[1]["cubes"] += [cube([-1, 15, -6], [2, 3, 1], "crest"), cube([-1, 14, -4], [2, 3, 1], "crest"),
                          cube([-3, 14, -8], [1, 3, 1], "horn"), cube([2, 14, -8], [1, 3, 1], "horn")]
    for i in range(4):
        bones[0]["cubes"].append(cube([-1, 13, -3 + i * 2.5], [2, 2, 2], "crest"))
    for side, name in ((1, "wing_l"), (-1, "wing_r")):
        bones.append(bone(name, [side * 3, 12, -1],
                          dragon_wing(side, (side * 3, 11, -1), 13),
                          parent="body"))
    return bones


# ------------------------------------------------------------------ palettes

def P(**kw):
    return kw

GREY, SLATE = (150, 154, 160, 255), (92, 98, 108, 255)
LOOKS = [
    # name, rarity, entity, builder, display-name, palette, pattern, wings
    dict(name="common", rarity="common", entity="wolf", build=look_common, title="Волк",
         palette=P(body=GREY, head=(168, 172, 178, 255), snout=(205, 205, 205, 255), nose=(24, 24, 28, 255),
                   ear=SLATE, leg=(128, 132, 140, 255), paw=SLATE, tail=(138, 142, 150, 255), tail_tip=(200, 200, 205, 255)),
         pattern="belly"),
    dict(name="uncommon", rarity="uncommon", entity="turtle", build=look_uncommon, title="Черепаха",
         palette=P(body=(120, 160, 70, 255), head=(132, 170, 78, 255), snout=(150, 185, 92, 255),
                   leg=(110, 150, 62, 255), tail=(110, 150, 62, 255), shell=(54, 120, 48, 255),
                   shell_top=(70, 140, 56, 255), moss=(112, 176, 70, 255), crystal=(120, 255, 150, 255)),
         pattern="hex"),
    dict(name="rare", rarity="rare", entity="wolf", build=look_rare, title="Лазурный волк",
         palette=P(body=(40, 110, 215, 255), head=(52, 128, 232, 255), snout=(220, 238, 255, 255),
                   nose=(12, 18, 40, 255), ear=(28, 80, 170, 255), leg=(34, 96, 196, 255),
                   paw=(220, 238, 255, 255), tail=(40, 110, 215, 255), tail_tip=(120, 220, 255, 255),
                   crystal=(110, 225, 255, 255)),
         pattern="belly"),
    dict(name="epic", rarity="epic", entity="ocelot", build=look_epic_tiger, title="Тигр",
         palette=P(body=(150, 60, 220, 255), head=(164, 76, 232, 255), snout=(235, 220, 245, 255),
                   nose=(40, 10, 50, 255), ear=(100, 30, 150, 255), leg=(140, 54, 205, 255),
                   paw=(235, 220, 245, 255), tail=(150, 60, 220, 255), tail_tip=(255, 255, 255, 255),
                   stripe=(60, 14, 100, 255)),
         pattern="stripes"),
    dict(name="legendary", rarity="legendary", entity="ocelot", build=look_legendary_lion, title="Лев",
         palette=P(body=(238, 168, 40, 255), head=(246, 182, 54, 255), snout=(250, 225, 150, 255),
                   nose=(60, 30, 10, 255), ear=(150, 90, 20, 255), leg=(226, 154, 34, 255),
                   paw=(150, 90, 20, 255), tail=(238, 168, 40, 255), mane=(150, 88, 24, 255),
                   mane_dark=(100, 56, 16, 255)),
         pattern="belly"),
    dict(name="mythic", rarity="mythic", entity="fox", build=look_mythic_kitsune, title="Кицунэ",
         palette=P(body=(240, 110, 190, 255), head=(250, 130, 205, 255), snout=(255, 225, 240, 255),
                   nose=(70, 10, 50, 255), ear=(255, 160, 220, 255), leg=(230, 96, 178, 255),
                   paw=(255, 225, 240, 255), tail=(240, 110, 190, 255), tail_tip=(255, 235, 248, 255),
                   crystal=(255, 170, 235, 255), crystal_wing=(255, 200, 245, 255)),
         pattern="belly", wings=True),
    dict(name="ancient", rarity="ancient", entity="horse", build=look_ancient_stag, title="Древний олень",
         palette=P(body=(50, 170, 165, 255), head=(60, 184, 178, 255), snout=(190, 240, 232, 255),
                   nose=(10, 40, 44, 255), ear=(40, 140, 140, 255), leg=(44, 150, 148, 255),
                   paw=(20, 90, 96, 255), tail_tip=(220, 255, 250, 255),
                   antler=(150, 225, 210, 255), antler_glow=(130, 255, 235, 255)),
         pattern="spots"),
    dict(name="divine", rarity="divine", entity="wolf", build=look_divine_angel, title="Ангел",
         palette=P(body=(244, 244, 248, 255), head=(252, 252, 255, 255), snout=(255, 255, 255, 255),
                   nose=(40, 36, 30, 255), ear=(232, 232, 240, 255), leg=(236, 236, 244, 255),
                   paw=(255, 255, 255, 255), tail=(244, 244, 248, 255), tail_tip=(255, 240, 200, 255),
                   horn=(240, 196, 80, 255), halo=(255, 226, 110, 255), gold=(240, 196, 80, 255),
                   wing=(250, 250, 255, 255), wing_bone=(240, 214, 140, 255)),
         pattern="belly", wings=True),
    dict(name="forbidden", rarity="forbidden", entity="wolf", build=look_forbidden_abyss, title="Дракон Бездны",
         palette=P(body=(24, 20, 26, 255), head=(32, 26, 32, 255), snout=(40, 30, 36, 255),
                   nose=(220, 40, 30, 255), ear=(150, 20, 24, 255), leg=(20, 16, 22, 255),
                   paw=(120, 16, 20, 255), tail=(24, 20, 26, 255), spike=(210, 30, 36, 255),
                   fang=(245, 230, 220, 255)),
         pattern="cracks"),
    dict(name="absolute", rarity="absolute", entity="wolf", build=look_absolute_dragon, title="Звёздный дракон",
         palette=P(body=(236, 232, 250, 255), head=(246, 244, 255, 255), snout=(255, 255, 255, 255),
                   nose=(60, 30, 120, 255), ear=(150, 110, 235, 255), leg=(226, 222, 245, 255),
                   paw=(150, 110, 235, 255), tail=(236, 232, 250, 255), tail_tip=(160, 110, 255, 255),
                   crest=(140, 90, 230, 255), horn=(190, 160, 255, 255),
                   wing=(130, 80, 220, 255), wing_bone=(240, 236, 255, 255)),
         pattern="belly", wings=True),
]

ENTITY_VARIANTS = {}   # entity -> [look names]; variant = index + 1
for _l in LOOKS:
    ENTITY_VARIANTS.setdefault(_l["entity"], []).append(_l["name"])

EYE = {"default": (255, 240, 120, 255), "divine": (255, 220, 90, 255), "forbidden": (255, 60, 40, 255),
       "absolute": (190, 120, 255, 255), "rare": (150, 240, 255, 255), "mythic": (255, 255, 255, 255),
       "ancient": (130, 255, 235, 255)}
GLOW_TAGS = {"crystal", "crystal_wing", "antler_glow", "halo", "spike", "crest"}


# ------------------------------------------------------------ UV pack + paint

def _dims(c):
    return [math.ceil(v) for v in c["size"]]


def pack_uvs(bones):
    """Shelf-pack every cube's box-UV into the 128x128 sheet."""
    x = y = shelf = 0
    for b in bones:
        for c in b["cubes"]:
            w, h, d = _dims(c)
            cw, ch = 2 * (d + w), d + h
            if x + cw > TEX:
                x, y, shelf = 0, y + shelf, 0
            if y + ch > TEX:
                raise SystemExit("rarity look does not fit the 128x128 texture sheet")
            c["uv"] = [x, y]
            x += cw
            shelf = max(shelf, ch)


FACE_ORDER = ("top", "bottom", "right", "front", "left", "back")


def faces(c):
    (u, v), (w, h, d) = c["uv"], _dims(c)
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_texture(look, bones):
    pal = look["palette"]
    seed = sum(ord(ch) for ch in look["name"])
    px = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
    eye = EYE.get(look["name"], EYE["default"])
    for b in bones:
        for ci, c in enumerate(b["cubes"]):
            tag = c["tag"]
            base = pal.get(tag, pal["body"])
            for face, (fx, fy, fw, fh) in faces(c).items():
                shade = {"top": 1.14, "bottom": 0.68, "front": 1.0, "back": 0.86}.get(face, 0.92)
                for ly in range(fh):
                    for lx in range(fw):
                        n = _hash(fx + lx, fy + ly, seed + ci)
                        col = _mul(base, shade * (0.93 + 0.14 * n))
                        pat = look["pattern"]
                        if tag in ("body", "leg", "tail", "head") and pat != "none":
                            col = _pattern(pat, tag, face, lx, ly, fw, fh, col, base, pal, n, seed)
                        if tag in GLOW_TAGS:
                            edge = lx in (0, fw - 1) or ly in (0, fh - 1)
                            col = _mix(col, (255, 255, 255, 255), 0.18 if edge else 0.40 * n)
                        if tag in ("wing", "crystal_wing") and (lx + ly) % 4 == 0:
                            col = _mul(col, 1.18)
                        px[fy + ly][fx + lx] = col
                if tag == "head" and face == "front" and fw >= 4:
                    ey = max(1, int(fh * 0.38))
                    for ex in (max(0, int(fw * 0.16)), min(fw - 2, int(fw * 0.66))):
                        for dx in (0, 1):
                            if ex + dx < fw:
                                px[fy + ey][fx + ex + dx] = eye
                                if ey + 1 < fh:
                                    px[fy + ey + 1][fx + ex + dx] = _mul(eye, 0.75)
                if tag == "head" and face == "front" and look["name"] == "forbidden":
                    for ex in range(fw):  # brow shadow
                        px[fy + max(0, int(fh * 0.38) - 1)][fx + ex] = _mul(px[fy + max(0, int(fh * 0.38) - 1)][fx + ex], 0.6)
    return px


def _pattern(pat, tag, face, lx, ly, fw, fh, col, base, pal, n, seed):
    side = face in ("right", "left", "front", "back")
    if pat == "belly" and (face == "bottom" or (side and ly >= fh * 0.7)):
        return _mix(col, (255, 255, 255, 255), 0.30)
    if pat == "stripes":
        axis = lx if side else ly
        if (axis + seed) % 4 < 2 and not (face == "bottom"):
            return _mul(pal["stripe"], 0.9 + 0.2 * n)
        if face == "bottom" or (side and ly >= fh * 0.75):
            return _mix(col, (255, 255, 255, 255), 0.55)
    if pat == "hex":
        if lx % 4 == 0 or ly % 4 == 0:
            return _mul(col, 0.7)
    if pat == "spots" and n < 0.10 and face in ("top", "left", "right"):
        return (246, 252, 250, 255)
    if pat == "cracks":
        if (lx * 3 + ly * 5 + seed) % 11 == 0 and face != "bottom":
            return _mix(col, (255, 90, 30, 255), 0.85)
        if n > 0.93:
            return _mix(col, (255, 60, 30, 255), 0.5)
    return col


# ---------------------------------------------------------- JSON generation

def geometry_json(look, bones):
    out = []
    for b in bones:
        ob = {"name": b["name"], "pivot": b["pivot"]}
        if b.get("parent"):
            ob["parent"] = b["parent"]
        if b.get("rotation"):
            ob["rotation"] = b["rotation"]
        ob["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
        out.append(ob)
    top = max(c["origin"][1] + c["size"][1] for b in bones for c in b["cubes"])
    span = max(abs(c["origin"][0]) + c["size"][0] for b in bones for c in b["cubes"])
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.nexora_look_{look['name']}",
                "texture_width": TEX, "texture_height": TEX,
                "visible_bounds_width": round(max(2.5, span / 8 + 1.5), 1),
                "visible_bounds_height": round(max(2.0, top / 16 + 1.0), 1),
                "visible_bounds_offset": [0, round(top / 32, 2), 0],
            },
            "bones": out,
        }],
    }


def animations_json():
    sp = "math.clamp(query.modified_move_speed * 2.0, 0, 1)"
    legs = {}
    # diagonal pairs move together, like vanilla quadrupeds
    for name, ph in (("leg0", 0), ("leg3", 0), ("leg1", 3.14159), ("leg2", 3.14159)):
        ang = f"query.modified_distance_moved * 38.17 + {ph}" if ph else "query.modified_distance_moved * 38.17"
        legs[name] = {"rotation": {"0.0": [f"math.cos({ang}) * {sp} * 50.0", 0, 0]}}
    flippers = {}
    for name, ph in (("flipper0", 0), ("flipper1", 3.14159), ("flipper2", 3.14159), ("flipper3", 0)):
        ang = f"query.modified_distance_moved * 38.17 + {ph}" if ph else "query.modified_distance_moved * 38.17"
        flippers[name] = {"rotation": {"0.0": [0, f"math.cos({ang}) * {sp} * 32.0", 0]}}
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.nexora_look.idle": {
                "loop": True, "animation_length": 4.0,
                "bones": {
                    "head": {"rotation": {"0.0": [0, 0, 0], "2.0": [-3, 0, 0], "4.0": [0, 0, 0]}},
                    "tail": {"rotation": {"0.0": [0, -8, 0], "1.0": [0, 8, 0], "2.0": [0, -8, 0],
                                          "3.0": [0, 8, 0], "4.0": [0, -8, 0]}},
                    "tail2": {"rotation": {"0.0": [0, 12, 0], "2.0": [0, -12, 0], "4.0": [0, 12, 0]}},
                    # quick vanilla-style ear flick every few seconds
                    "ear_l": {"rotation": {"0.0": [0, 0, 0], "2.6": [0, 0, 0], "2.75": [0, 0, 18],
                                           "2.9": [0, 0, 0], "4.0": [0, 0, 0]}},
                    "ear_r": {"rotation": {"0.0": [0, 0, 0], "1.2": [0, 0, 0], "1.35": [0, 0, -18],
                                           "1.5": [0, 0, 0], "4.0": [0, 0, 0]}},
                },
            },
            "animation.nexora_look.walk": {
                "loop": True, "animation_length": 1.0,
                "bones": {**legs, **flippers,
                          "head": {"rotation": {"0.0": [f"math.cos(query.modified_distance_moved * 38.17 * 2.0) * {sp} * 3.0", 0, 0]}},
                          "tail": {"rotation": {"0.0": [f"{sp} * 12.0", f"math.sin(query.modified_distance_moved * 38.17) * {sp} * 10.0", 0]}},
                          "ear_l": {"rotation": {"0.0": [f"{sp} * -8.0", 0, f"{sp} * -6.0"]}},
                          "ear_r": {"rotation": {"0.0": [f"{sp} * -8.0", 0, f"{sp} * 6.0"]}},
                          "body": {"position": {"0.0": [0, f"math.abs(math.cos(query.modified_distance_moved * 38.17)) * {sp} * 0.35", 0]}}},
            },
            "animation.nexora_look.wings": {
                "loop": True, "animation_length": 1.2,
                "bones": {
                    "wing_l": {"rotation": {"0.0": [0, 0, -8], "0.6": [0, 0, 22], "1.2": [0, 0, -8]}},
                    "wing_r": {"rotation": {"0.0": [0, 0, 8], "0.6": [0, 0, -22], "1.2": [0, 0, 8]}},
                },
            },
        },
    }


def patch_entity(root, entity, names):
    """Add variant arrays/animations to an existing client entity + controller."""
    epath = root / "entity" / f"nexora_{entity}.entity.json"
    data = json.loads(epath.read_text(encoding="utf-8"))
    desc = data["minecraft:client_entity"]["description"]
    for n in names:
        desc["geometry"][f"look_{n}"] = f"geometry.nexora_look_{n}"
        desc["textures"][f"look_{n}"] = f"textures/entity/looks/nexora_{n}"
    desc["animations"].update({"look_idle": "animation.nexora_look.idle",
                               "look_walk": "animation.nexora_look.walk",
                               "look_wings": "animation.nexora_look.wings"})
    animate = [a for a in desc["scripts"]["animate"]
               if not (a in ("look_idle", "look_wings")
                       or (isinstance(a, dict) and ("look_walk" in a or "look_wings" in a)))]
    animate += ["look_idle", {"look_walk": "query.modified_move_speed > 0.01"}, "look_wings"]
    desc["scripts"]["animate"] = animate
    epath.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    rpath = root / "render_controllers" / f"nexora_{entity}.render.json"
    ctrl_name = next(iter(json.loads(rpath.read_text(encoding="utf-8"))["render_controllers"]))
    geos = ["Geometry.default"] + [f"Geometry.look_{n}" for n in names]
    texs = ["Texture.default"] + [f"Texture.look_{n}" for n in names]
    rc = {"format_version": "1.8.0", "render_controllers": {ctrl_name: {
        "arrays": {"geometries": {"Array.geo": geos}, "textures": {"Array.tex": texs}},
        "geometry": "Array.geo[query.variant]",
        "materials": [{"*": "Material.default"}],
        "textures": ["Array.tex[query.variant]"]}}}
    rpath.write_text(json.dumps(rc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_png_rgba(path, px, chunk, zlib, struct):
    raw = b"".join(b"\x00" + bytes(ch for p in row for ch in p) for row in px)
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", TEX, TEX, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def build_all(root, chunk, zlib, struct):
    built = {}
    for look in LOOKS:
        bones = look["build"]()
        pack_uvs(bones)
        built[look["name"]] = bones
        gpath = root / "models" / "entity" / f"nexora_look_{look['name']}.geo.json"
        gpath.write_text(json.dumps(geometry_json(look, bones), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        write_png_rgba(root / "textures" / "entity" / "looks" / f"nexora_{look['name']}.png",
                       paint_texture(look, bones), chunk, zlib, struct)
    apath = root / "animations" / "nexora_looks.animation.json"
    apath.write_text(json.dumps(animations_json(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for entity, names in ENTITY_VARIANTS.items():
        patch_entity(root, entity, names)
    return built

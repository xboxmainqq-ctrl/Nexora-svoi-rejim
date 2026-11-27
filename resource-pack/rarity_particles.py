#!/usr/bin/env python3
"""NEXORA BEASTS — custom particle auras (textures + Bedrock particle effects).

Ten rarities x four layers each -> nexora:aura_<rarity>_<1..4>.
Textures are drawn procedurally (pure Python, white-on-transparent, tinted by
the particle gradient): magic circles with rune bands, sparkles, petals,
feathers, embers, smoke and a gem halo.

The server (RarityAuras.java) spawns the four layers of the strongest pet's
rarity around the owner every few ticks.
"""
from pathlib import Path
import json
import math

RARITIES = ["common", "uncommon", "rare", "epic", "legendary",
            "mythic", "ancient", "divine", "forbidden", "absolute"]
LAYERS_PER_RARITY = 4

# (light, mid, dark) tint per rarity — same family as the lettering gradients.
PALETTE = {
    "common":    ("#ECEFF4", "#ACB2BC", "#707884"),
    "uncommon":  ("#CDFFAC", "#5CDE70", "#1E9652"),
    "rare":      ("#ACF2FF", "#46ACFF", "#2864E2"),
    "epic":      ("#F2C0FF", "#BE64FF", "#7632C8"),
    "legendary": ("#FFF67A", "#FFC034", "#FF6E1E"),
    "mythic":    ("#FFC0EC", "#FF70CA", "#BE3CD8"),
    "ancient":   ("#ACFFEC", "#3ED8CA", "#1A8898"),
    "divine":    ("#FFFFEE", "#FFECA0", "#ECBA48"),
    "forbidden": ("#FF9884", "#EC343E", "#840C2A"),
    "absolute":  ("#FFFFFF", "#C0ACFF", "#7E5CEC"),
}

A = "variable.particle_age"
L = "variable.particle_lifetime"
R1, R2, R3, R4 = (f"variable.particle_random_{i}" for i in (1, 2, 3, 4))
T = f"({A} / {L})"

# ------------------------------------------------------------ texture art

def _png(path, w, h, alpha, chunk, zlib, struct):
    raw = b"".join(
        b"\x00" + bytes(v for a in row for v in (255, 255, 255, max(0, min(255, int(a * 255)))))
        for row in alpha)
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def _seg_d(px, py, x1, y1, x2, y2):
    dx, dy = x2 - x1, y2 - y1
    ln = dx * dx + dy * dy
    t = 0.0 if ln == 0 else max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / ln))
    return math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))


def _blank(n):
    return [[0.0] * n for _ in range(n)]


def _stroke(img, segs, width, glow=0.30):
    """Soft anti-aliased line work with a faint halo."""
    n = len(img)
    for y in range(n):
        for x in range(n):
            d = min(_seg_d(x + 0.5, y + 0.5, *s) for s in segs)
            core = max(0.0, 1.0 - d / width) ** 1.3
            halo = glow * math.exp(-(d / (width * 2.6)) ** 2)
            img[y][x] = max(img[y][x], min(1.0, max(core, halo)))


def _ring_segs(cx, cy, r, steps=72):
    pts = [(cx + math.cos(2 * math.pi * i / steps) * r, cy + math.sin(2 * math.pi * i / steps) * r)
           for i in range(steps + 1)]
    return [(*pts[i], *pts[i + 1]) for i in range(steps)]


def _star_segs(cx, cy, r, n, k, rot=-math.pi / 2):
    pts = [(cx + math.cos(rot + 2 * math.pi * i * k / n) * r, cy + math.sin(rot + 2 * math.pi * i * k / n) * r)
           for i in range(n + 1)]
    return [(*pts[i], *pts[i + 1]) for i in range(n)]


def _glyph_segs(cx, cy, r0, r1, slots, seed):
    """Short rune-like strokes spread along a ring band."""
    segs, s = [], seed
    for i in range(slots):
        a = 2 * math.pi * i / slots
        s = (s * 1103515245 + 12345) & 0x7FFFFFFF
        kind = s % 4
        ra, rb = r0, r1
        ca, sa = math.cos(a), math.sin(a)
        ta, tb = -sa, ca  # tangent
        mid = (ra + rb) / 2
        if kind == 0:     # radial bar
            segs.append((cx + ca * ra, cy + sa * ra, cx + ca * rb, cy + sa * rb))
        elif kind == 1:   # "T"
            segs.append((cx + ca * ra, cy + sa * ra, cx + ca * rb, cy + sa * rb))
            segs.append((cx + ca * rb + ta * 2.2, cy + sa * rb + tb * 2.2, cx + ca * rb - ta * 2.2, cy + sa * rb - tb * 2.2))
        elif kind == 2:   # chevron
            segs.append((cx + ca * mid + ta * 2.4, cy + sa * mid + tb * 2.4, cx + ca * rb, cy + sa * rb))
            segs.append((cx + ca * rb, cy + sa * rb, cx + ca * mid - ta * 2.4, cy + sa * mid - tb * 2.4))
        else:             # diagonal pair
            segs.append((cx + ca * ra + ta * 2, cy + sa * ra + tb * 2, cx + ca * rb - ta * 2, cy + sa * rb - tb * 2))
            segs.append((cx + ca * mid, cy + sa * mid, cx + ca * mid + ta * 2.6, cy + sa * mid + tb * 2.6))
    return segs


def tex_glow(n=32):
    img = _blank(n)
    c = n / 2
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c) / c
            img[y][x] = min(1.0, max(0.0, 1 - d) ** 2.2 + 0.95 * math.exp(-(d / 0.14) ** 2))
    return img


def tex_spark(n=32):
    img = _blank(n)
    c = n / 2
    for y in range(n):
        for x in range(n):
            dx, dy = (x + 0.5 - c) / c, (y + 0.5 - c) / c
            long_h = math.exp(-(dy * dy) / 0.0035 - (dx * dx) / 0.35)
            long_v = math.exp(-(dx * dx) / 0.0035 - (dy * dy) / 0.35)
            diag = 0.45 * (math.exp(-((dx - dy) ** 2) / 0.01 - (dx + dy) ** 2 / 0.5)
                           + math.exp(-((dx + dy) ** 2) / 0.01 - (dx - dy) ** 2 / 0.5))
            core = math.exp(-(dx * dx + dy * dy) / 0.02)
            img[y][x] = min(1.0, long_h + long_v + diag + core)
    return img


def tex_ember(n=32):
    img = _blank(n)
    c = n / 2
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x + 0.5 - c) / c, abs(y + 0.5 - c) / (c * 1.35)
            d = dx + dy
            img[y][x] = min(1.0, max(0.0, 1 - d) ** 0.8 * 1.1) if d < 1 else 0.0
            img[y][x] = max(img[y][x], 0.35 * math.exp(-(dx * dx + dy * dy) / 0.45))
    return img


def tex_petal(n=32):
    img = _blank(n)
    c = n / 2
    for y in range(n):
        for x in range(n):
            u, v = (x + 0.5 - c) / c, (y + 0.5 - c) / c
            # teardrop: wide at the bottom, pointed at the top
            vv = (v + 1) / 2
            w = 0.62 * math.sin(math.pi * min(1, vv * 1.05)) ** 0.8 * (0.35 + 0.65 * vv)
            if abs(u) < w:
                edge = 1 - abs(u) / max(w, 1e-6)
                img[y][x] = min(1.0, 0.55 + 0.45 * edge) * (0.65 + 0.35 * math.exp(-u * u * 18))
    return img


def tex_feather(n=32):
    img = _blank(n)
    segs = [(n * 0.5, n * 0.06, n * 0.5, n * 0.94)]
    for i in range(1, 9):
        y0 = n * (0.12 + i * 0.095)
        segs.append((n * 0.5, y0, n * (0.5 - 0.26 + i * 0.012), y0 - n * 0.13))
        segs.append((n * 0.5, y0, n * (0.5 + 0.26 - i * 0.012), y0 - n * 0.13))
    _stroke(img, segs, 1.15, glow=0.25)
    return img


def tex_smoke(n=64):
    img = _blank(n)
    c = n / 2
    s = 7
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c) / c
            s = (s * 1103515245 + 12345) & 0x7FFFFFFF
            noise = 0.78 + 0.22 * ((s >> 8) % 100) / 100.0
            img[y][x] = max(0.0, 1 - d) ** 1.4 * noise
    return img


def circle_tex(kind, n=128):
    img = _blank(n)
    c = n / 2
    R = c - 3
    segs = []
    segs += _ring_segs(c, c, R, 120)               # outer ring
    segs += _ring_segs(c, c, R * 0.93, 120)        # double outer line
    segs += _ring_segs(c, c, R * 0.60, 96)         # inner ring
    for i in range(72):                            # tick marks on the outer band
        a = 2 * math.pi * i / 72
        r0, r1 = R * 0.93, R * (0.975 if i % 6 else 1.0)
        segs.append((c + math.cos(a) * r0, c + math.sin(a) * r0, c + math.cos(a) * r1, c + math.sin(a) * r1))
    segs += _glyph_segs(c, c, R * 0.70, R * 0.84, 24, {"tri": 3, "hex": 11, "star": 5, "oct": 17,
                                                      "sun": 23, "flower": 29, "tri_inv": 31}[kind])
    figure = {
        "tri": [_star_segs(c, c, R * 0.88, 3, 1), _star_segs(c, c, R * 0.56, 3, 1, math.pi / 2)],
        "tri_inv": [_star_segs(c, c, R * 0.88, 3, 1, math.pi / 2), _star_segs(c, c, R * 0.56, 3, 1)],
        "hex": [_star_segs(c, c, R * 0.88, 3, 1), _star_segs(c, c, R * 0.88, 3, 1, math.pi / 2)],
        "star": [_star_segs(c, c, R * 0.88, 5, 2)],
        "oct": [_star_segs(c, c, R * 0.88, 8, 3), _star_segs(c, c, R * 0.50, 8, 1)],
        "sun": [],
        "flower": [],
    }[kind]
    for f in figure:
        segs += f
    if kind == "sun":
        for i in range(16):
            a = 2 * math.pi * i / 16
            r1 = R * (0.55 if i % 2 else 0.36)
            segs.append((c + math.cos(a) * R * 0.60, c + math.sin(a) * R * 0.60,
                         c + math.cos(a) * r1, c + math.sin(a) * r1))
        segs += _ring_segs(c, c, R * 0.22, 48)
    if kind == "flower":
        for i in range(8):
            a = 2 * math.pi * i / 8
            segs += _ring_segs(c + math.cos(a) * R * 0.30, c + math.sin(a) * R * 0.30, R * 0.30, 40)
    _stroke(img, segs, 1.25, glow=0.28)
    # soft centre glow
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c) / R
            img[y][x] = min(1.0, img[y][x] + 0.20 * max(0.0, 1 - d / 0.60) ** 2)
    return img


def tex_halo(n=128):
    img = _blank(n)
    c = n / 2
    R = c - 4
    segs = _ring_segs(c, c, R, 120) + _ring_segs(c, c, R * 0.90, 120) + _ring_segs(c, c, R * 0.80, 120)
    _stroke(img, segs, 1.3, glow=0.35)
    for i in range(12):                      # gems
        a = 2 * math.pi * i / 12
        gx, gy = c + math.cos(a) * R * 0.90, c + math.sin(a) * R * 0.90
        for y in range(n):
            for x in range(n):
                d = math.hypot(x + 0.5 - gx, y + 0.5 - gy)
                if d < 4.2:
                    img[y][x] = max(img[y][x], 1.0 - d / 5.0)
    return img


TEXTURES = {
    "glow": tex_glow, "spark": tex_spark, "ember": tex_ember, "petal": tex_petal,
    "feather": tex_feather, "smoke": tex_smoke, "halo": tex_halo,
    **{f"circle_{k}": (lambda k=k: circle_tex(k)) for k in ("tri", "tri_inv", "hex", "star", "oct", "sun", "flower")},
}

# ---------------------------------------------------------- particle effects

def _grad(pal, peak=1.0, fade_in=0.15, fade_out=0.78, hue_shift=True):
    a = lambda v: f"{max(0, min(255, round(255 * v))):02X}"
    light, mid, dark = pal
    stops = {
        "0.0": "#" + a(0) + light[1:],
        f"{fade_in}": "#" + a(peak) + light[1:],
        "0.5": "#" + a(peak) + mid[1:],
        f"{fade_out}": "#" + a(peak * 0.85) + (dark[1:] if hue_shift else mid[1:]),
        "1.0": "#" + a(0) + (dark[1:] if hue_shift else mid[1:]),
    }
    return stops


def effect(ident, tex, pal, *, lifetime, pos, size, rate=None, instant=None, active=0.5,
           facing="lookat_xyz", spin=None, material="particles_add", peak=1.0, tex_px=32,
           uv_size=None, max_particles=40, fade=(0.15, 0.78)):
    comps = {
        "minecraft:emitter_local_space": {"position": True, "rotation": False},
        "minecraft:emitter_lifetime_once": {"active_time": active if rate else 0.1},
        "minecraft:emitter_shape_point": {"offset": [0, 0, 0], "direction": [0, 0, 0]},
        "minecraft:particle_lifetime_expression": {"max_lifetime": lifetime},
        "minecraft:particle_initial_speed": 0,
        "minecraft:particle_motion_parametric": {"relative_position": list(pos)},
        "minecraft:particle_appearance_billboard": {
            "size": list(size),
            "facing_camera_mode": facing,
            "uv": {"texture_width": tex_px, "texture_height": tex_px, "uv": [0, 0], "uv_size": [tex_px, tex_px]},
        },
        "minecraft:particle_appearance_tinting": {
            "color": {"interpolant": f"{A} / {L}", "gradient": _grad(pal, peak, *fade)}},
    }
    if rate:
        comps["minecraft:emitter_rate_steady"] = {"spawn_rate": rate, "max_particles": max_particles}
    else:
        comps["minecraft:emitter_rate_instant"] = {"num_particles": instant or 1}
    if spin is not None:
        comps["minecraft:particle_initial_spin"] = {"rotation": spin[0], "rotation_rate": spin[1]}
    return {"format_version": "1.10.0", "particle_effect": {
        "description": {"identifier": ident,
                        "basic_render_parameters": {"material": material, "texture": f"textures/particle/nexora_{tex}"}},
        "components": comps}}


def _cs(angle):  # cos/sin pair helpers for parametric motion
    return f"math.cos({angle})", f"math.sin({angle})"


# Motion/size builders -------------------------------------------------------
def orbit(radius, height, speed, wobble=0.12):
    ang = f"{R1} * 360 + {A} * {speed}"
    c, s = _cs(ang)
    return [f"{c} * {radius}", f"{height} + math.sin({A} * 200 + {R2} * 360) * {wobble}", f"{s} * {radius}"]


def helix(radius, speed, rise, phase=0, grow=0.0):
    ang = f"{phase} + {A} * {speed}"
    c, s = _cs(ang)
    r = f"({radius} + {A} * {grow})"
    return [f"{c} * {r}", f"0.1 + {A} * {rise}", f"{s} * {r}"]


def rise(radius, speed, swirl=50):
    ang = f"{R1} * 360 + {A} * {swirl}"
    c, s = _cs(ang)
    r = f"({R2} * {radius})"
    return [f"{c} * {r}", f"0.05 + {A} * {speed}", f"{s} * {r}"]


def fall(radius, y0, speed, swirl=40):
    ang = f"{R1} * 360 + {A} * {swirl}"
    c, s = _cs(ang)
    return [f"{c} * {radius}", f"{y0} - {A} * {speed}", f"{s} * {radius}"]


def flat(y):
    return ["0", str(y), "0"]


def sq(base, var=0.0):
    """Square billboard size (half-extent) with optional per-particle variation + pulse."""
    e = f"{base} + {R3} * {var}" if var else f"{base}"
    return [e, e]


def pulse(base, amount=0.25):
    e = f"{base} * (1 + {amount} * math.sin({A} * 360 * 1.5 + {R3} * 360))"
    return [e, e]


def grow_in(base, var=0.0):
    e = f"({base} + {R3} * {var}) * math.sin({T} * 180) "
    return [e, e]


def build_layers():
    """Return {rarity: [effect, effect, effect, effect]}."""
    out = {}
    for name in RARITIES:
        pal = PALETTE[name]
        idn = lambda i, n=name: f"nexora:aura_{n}_{i}"
        L4 = []
        if name == "common":
            L4 = [
                effect(idn(1), "circle_tri", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(0.95),
                       facing="emitter_transform_xz", spin=("0", 22), peak=0.30, tex_px=128),
                effect(idn(2), "glow", pal, lifetime=1.1, rate=7, pos=orbit(0.8, 0.45, 110), size=grow_in(0.06, 0.03)),
                effect(idn(3), "spark", pal, lifetime=1.0, rate=3, pos=rise(0.7, 0.55), size=grow_in(0.07, 0.03), peak=0.8),
                effect(idn(4), "glow", pal, lifetime=1.2, rate=4, pos=rise(0.9, 0.35), size=grow_in(0.05, 0.02), peak=0.5),
            ]
        elif name == "uncommon":
            L4 = [
                effect(idn(1), "circle_tri", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.05),
                       facing="emitter_transform_xz", spin=("0", 28), peak=0.42, tex_px=128),
                effect(idn(2), "petal", pal, lifetime=1.6, rate=7, pos=fall(0.9, 1.9, 1.1, 60), size=grow_in(0.13, 0.04),
                       material="particles_alpha", facing="rotate_xyz", spin=(f"{R4} * 360", 80), peak=0.95),
                effect(idn(3), "glow", pal, lifetime=1.4, rate=8, pos=rise(0.8, 0.8, 70), size=grow_in(0.07, 0.04), peak=0.7),
                effect(idn(4), "spark", pal, lifetime=1.0, rate=3, pos=orbit(0.9, 1.0, 140, 0.3), size=grow_in(0.09, 0.04)),
            ]
        elif name == "rare":
            L4 = [
                effect(idn(1), "circle_hex", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.15),
                       facing="emitter_transform_xz", spin=("0", -32), peak=0.5, tex_px=128),
                effect(idn(2), "glow", pal, lifetime=1.1, rate=10, pos=orbit(0.95, 1.0, 190, 0.18), size=grow_in(0.13, 0.03)),
                effect(idn(3), "glow", pal, lifetime=1.1, rate=10, pos=orbit(0.95, 1.0, -190, 0.18), size=grow_in(0.13, 0.03)),
                effect(idn(4), "spark", pal, lifetime=1.2, rate=6, pos=rise(0.9, 0.9, -80), size=grow_in(0.10, 0.05)),
            ]
        elif name == "epic":
            L4 = [
                effect(idn(1), "circle_star", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.2),
                       facing="emitter_transform_xz", spin=("0", 36), peak=0.55, tex_px=128),
                effect(idn(2), "glow", pal, lifetime=1.2, rate=18, pos=helix(0.75, 220, 1.5, 0), size=grow_in(0.09)),
                effect(idn(3), "glow", pal, lifetime=1.2, rate=18, pos=helix(0.75, 220, 1.5, 180), size=grow_in(0.09)),
                effect(idn(4), "spark", pal, lifetime=1.3, rate=6, pos=rise(1.0, 1.1, 90), size=grow_in(0.11, 0.05)),
            ]
        elif name == "legendary":
            L4 = [
                effect(idn(1), "circle_sun", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.3),
                       facing="emitter_transform_xz", spin=("0", 40), peak=0.62, tex_px=128),
                effect(idn(2), "ember", pal, lifetime=1.4, rate=16, pos=rise(1.0, 1.5, 60), size=grow_in(0.10, 0.06), peak=0.95),
                effect(idn(3), "glow", pal, lifetime=1.1, rate=14, pos=orbit(0.8, 1.05, -170, 0.25), size=grow_in(0.11, 0.03)),
                effect(idn(4), "spark", pal, lifetime=1.0, rate=7, pos=orbit(1.15, 0.6, 130, 0.35), size=pulse(0.12, 0.35)),
            ]
        elif name == "mythic":
            L4 = [
                effect(idn(1), "circle_flower", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.25),
                       facing="emitter_transform_xz", spin=("0", -34), peak=0.55, tex_px=128),
                effect(idn(2), "petal", pal, lifetime=1.7, rate=12, pos=fall(1.0, 2.2, 1.2, 70), size=grow_in(0.16, 0.05),
                       material="particles_alpha", facing="rotate_xyz", spin=(f"{R4} * 360", 110), peak=0.95),
                effect(idn(3), "glow", pal, lifetime=1.2, rate=12, pos=orbit(0.9, 0.6, 150, 0.2), size=grow_in(0.08, 0.04)),
                effect(idn(4), "spark", pal, lifetime=1.2, rate=8, pos=orbit(0.8, 1.5, -170, 0.25), size=pulse(0.12, 0.3)),
            ]
        elif name == "ancient":
            L4 = [
                effect(idn(1), "circle_oct", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.35),
                       facing="emitter_transform_xz", spin=("0", 22), peak=0.6, tex_px=128),
                effect(idn(2), "circle_hex", pal, lifetime=0.9, instant=1, pos=flat(0.09), size=sq(0.8),
                       facing="emitter_transform_xz", spin=("0", -46), peak=0.5, tex_px=128),
                effect(idn(3), "glow", pal, lifetime=1.5, rate=14, pos=rise(1.1, 1.0, 40), size=grow_in(0.09, 0.05), peak=0.8),
                effect(idn(4), "spark", pal, lifetime=1.2, rate=5, pos=orbit(1.2, 0.9, 80, 0.45), size=pulse(0.11, 0.3)),
            ]
        elif name == "divine":
            L4 = [
                effect(idn(1), "circle_sun", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.4),
                       facing="emitter_transform_xz", spin=("0", 26), peak=0.62, tex_px=128),
                effect(idn(2), "halo", pal, lifetime=0.9, instant=1, pos=flat(2.35), size=sq(0.5),
                       facing="emitter_transform_xz", spin=("0", 55), peak=0.95, tex_px=128),
                effect(idn(3), "feather", pal, lifetime=1.9, rate=8, pos=fall(0.95, 2.4, 1.15, 50), size=grow_in(0.14, 0.04),
                       material="particles_alpha", facing="rotate_xyz", spin=(f"{R4} * 360", 70), peak=0.95),
                effect(idn(4), "spark", pal, lifetime=1.3, rate=9, pos=rise(1.1, 1.5, 70), size=grow_in(0.12, 0.06)),
            ]
        elif name == "forbidden":
            L4 = [
                effect(idn(1), "circle_tri_inv", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.4),
                       facing="emitter_transform_xz", spin=("0", -42), peak=0.7, tex_px=128),
                effect(idn(2), "glow", pal, lifetime=1.3, rate=22, pos=helix(0.35, 300, 1.2, 0, 0.5), size=grow_in(0.10, 0.03)),
                effect(idn(3), "ember", pal, lifetime=1.3, rate=14, pos=rise(1.1, 1.7, 110), size=grow_in(0.10, 0.06), peak=0.95),
                effect(idn(4), "smoke", pal, lifetime=1.6, rate=6, pos=rise(0.8, 0.9, 30), size=grow_in(0.30, 0.10),
                       material="particles_alpha", peak=0.45, tex_px=64),
            ]
        else:  # absolute
            L4 = [
                effect(idn(1), "circle_star", pal, lifetime=0.9, instant=1, pos=flat(0.05), size=sq(1.5),
                       facing="emitter_transform_xz", spin=("0", 30), peak=0.65, tex_px=128),
                effect(idn(2), "circle_oct", pal, lifetime=0.9, instant=1, pos=flat(1.0), size=sq(0.9),
                       facing="emitter_transform_xz", spin=("0", -50), peak=0.35, tex_px=128),
                effect(idn(3), "halo", pal, lifetime=0.9, instant=1, pos=flat(2.45), size=sq(0.6),
                       facing="emitter_transform_xz", spin=("0", 70), peak=0.95, tex_px=128),
                effect(idn(4), "spark", pal, lifetime=1.2, rate=18, pos=orbit(1.15, 1.1, 200, 0.7), size=pulse(0.14, 0.4)),
            ]
        out[name] = L4
    return out


def build_all(root, chunk, zlib, struct):
    for name, fn in TEXTURES.items():
        img = fn()
        _png(root / "textures" / "particle" / f"nexora_{name}.png", len(img[0]), len(img), img, chunk, zlib, struct)
    layers = build_layers()
    for rarity, effects in layers.items():
        assert len(effects) == LAYERS_PER_RARITY
        for i, eff in enumerate(effects, start=1):
            path = root / "particles" / f"aura_{rarity}_{i}.particle.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(eff, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return layers

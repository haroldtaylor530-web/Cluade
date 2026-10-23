#!/usr/bin/env python3
"""Generates the fossil block assets: overlay textures (bones pressed into the rock), block models,
blockstates, item models, loot tables, the pickaxe tag and the language entries.

Every fossil block is a rock block (stone, deepslate or sandstone, using the game's own textures)
with a bone overlay drawn on top. Run from the repository root: python3 tools/gen_fossils.py
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "corpsedecay")
DATA = os.path.join(ROOT, "data")
MODID = "corpsedecay"

# rock the fossil is in: base textures (side, top, bottom), bone colours (light, mid, dark), hollow
HOSTS = {
    "stone": {
        "name": "Stone",
        "tex": ("minecraft:block/stone", "minecraft:block/stone", "minecraft:block/stone"),
        "bone": ((0xEA, 0xE2, 0xCC), (0xD0, 0xC4, 0xA4), (0xA2, 0x94, 0x76)),
        "hollow": (0x5A, 0x5A, 0x5C),
    },
    "deepslate": {
        "name": "Deepslate",
        "tex": ("minecraft:block/deepslate", "minecraft:block/deepslate_top", "minecraft:block/deepslate_top"),
        "bone": ((0xDC, 0xD5, 0xC4), (0xB8, 0xAE, 0x99), (0x84, 0x7A, 0x6A)),
        "hollow": (0x26, 0x26, 0x2B),
    },
    "sandstone": {
        "name": "Sandstone",
        "tex": ("minecraft:block/sandstone", "minecraft:block/sandstone_top", "minecraft:block/sandstone_bottom"),
        "bone": ((0x9C, 0x6E, 0x48), (0x7E, 0x55, 0x36), (0x5A, 0x3B, 0x24)),
        "hollow": (0xB0, 0x98, 0x66),
    },
    "red_sandstone": {
        "name": "Red Sandstone",
        "tex": ("minecraft:block/red_sandstone", "minecraft:block/red_sandstone_top", "minecraft:block/red_sandstone_bottom"),
        "bone": ((0xF0, 0xE2, 0xC6), (0xD8, 0xC4, 0x9E), (0xA8, 0x90, 0x6C)),
        "hollow": (0x7E, 0x3C, 0x14),
    },
    "shale": {
        "name": "Shale",
        "tex": ("corpsedecay:block/shale", "corpsedecay:block/shale_top", "corpsedecay:block/shale_top"),
        "bone": ((0xCE, 0xCE, 0xC8), (0xAC, 0xAC, 0xA6), (0x84, 0x84, 0x80)),
        "hollow": (0x24, 0x24, 0x28),
        "pieces": "shale",
    },
    "limestone": {
        "name": "Limestone",
        "tex": ("corpsedecay:block/limestone", "corpsedecay:block/limestone_top", "corpsedecay:block/limestone_top"),
        "bone": ((0x9E, 0x7E, 0x5A), (0x80, 0x62, 0x42), (0x5C, 0x46, 0x30)),
        "hollow": (0x9C, 0x94, 0x80),
        "pieces": "limestone",
    },
    "chalk": {
        "name": "Chalk",
        "tex": ("corpsedecay:block/chalk", "corpsedecay:block/chalk_top", "corpsedecay:block/chalk_top"),
        "bone": ((0x96, 0x82, 0x6A), (0x74, 0x62, 0x4C), (0x52, 0x44, 0x34)),
        "hollow": (0xC4, 0xC0, 0xB4),
        "pieces": "chalk",
    },
    "permafrost": {
        "name": "Permafrost",
        "tex": ("corpsedecay:block/permafrost", "corpsedecay:block/permafrost_top", "corpsedecay:block/permafrost_top"),
        "bone": ((0xDE, 0xCE, 0xA6), (0xBC, 0xA8, 0x80), (0x8E, 0x7A, 0x5A)),
        "hollow": (0x2A, 0x20, 0x18),
        "pieces": "permafrost",
    },
    "terracotta": {
        "name": "Terracotta",
        "tex": ("minecraft:block/terracotta", "minecraft:block/terracotta", "minecraft:block/terracotta"),
        "bone": ((0xF2, 0xE8, 0xD2), (0xDA, 0xCB, 0xAC), (0xAE, 0x9A, 0x7A)),
        "hollow": (0x5E, 0x38, 0x2A),
    },
}

# ---- the pictures: '#' bone, 'o' a hollow inside the bone, anything else empty ----------------------

SKULL_SIDE = [  # pointing right (the way the animal faces)
    "................",
    "................",
    "................",
    "....######......",
    "...#########....",
    "..###oo######...",
    "..###oo#########",
    "..##############",
    "..#######o######",
    "..##.#.#.#.#.#.#",
    "..##............",
    "..###.#.#.#.#...",
    "..###########...",
    "...#########....",
    "................",
    "................",
]
SKULL_FRONT = [
    "................",
    "................",
    "................",
    "....########....",
    "...##########...",
    "..############..",
    "..##oo####oo##..",
    "..##oo####oo##..",
    "..############..",
    "...####oo####...",
    "....###oo###....",
    "....#.#..#.#....",
    "....########....",
    "................",
    "................",
    "................",
]
SKULL_TOP = [  # pointing up
    "................",
    "......####......",
    "......####......",
    ".....######.....",
    ".....#o##o#.....",
    ".....######.....",
    "....########....",
    "...##########...",
    "..#o########o#..",
    "..#oo######oo#..",
    "..############..",
    "...##########...",
    "....########....",
    ".....######.....",
    "................",
    "................",
]
VERTEBRA_END = [  # a backbone seen end on
    "................",
    "................",
    "................",
    ".......##.......",
    "......####......",
    ".....######.....",
    ".....##oo##.....",
    ".....##oo##.....",
    ".....######.....",
    "......####......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
SPINE_SIDE = [
    "................",
    "................",
    "................",
    "#...#...#...#...",
    "#...#...#...#...",
    "##.###.###.###.#",
    "################",
    "################",
    "##.###.###.###.#",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
SPINE_TOP = [
    "......####......",
    "...##########...",
    "......####......",
    ".......##.......",
] * 4
RIBS_SIDE = SPINE_SIDE[:9] + [
    "#...#...#...#...",
    "#...#...#...#...",
    "...#...#...#...#",
    "...#...#...#...#",
    "..#...#...#...#.",
    "..#...#...#...#.",
    "................",
]
RIBS_FRONT = [
    "................",
    "................",
    "................",
    ".......##.......",
    "......####......",
    ".....######.....",
    "....##.oo.##....",
    "...#.##oo##.#...",
    "..#..######..#..",
    "..#...####...#..",
    ".#............#.",
    ".#............#.",
    ".#............#.",
    "..#..........#..",
    "...#........#...",
    "....##....##....",
]
RIBS_TOP = [
    "......####......",
    ".##############.",
    "#.....####.....#",
    ".......##.......",
] * 4
TAIL_SIDE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..#...#...#...#.",
    "################",
    ".##.###.###.###.",
    "..#...#...#...#.",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
TAIL_END = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......##.......",
    "......####......",
    "......####......",
    ".......##.......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
TAIL_TOP = [
    "......####......",
    ".......##.......",
    ".......##.......",
    ".......##.......",
] * 4
LIMB_SIDE = [
    ".....######.....",
    ".....######.....",
    "......####......",
] + [".......##......."] * 10 + [
    "......####......",
    ".....######.....",
    ".....######.....",
]
LIMB_END = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......####......",
    ".....##oo##.....",
    ".....##oo##.....",
    "......####......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
FOOT_SIDE = [
    ".....######.....",
    ".....######.....",
    "......####......",
] + [".......##......."] * 5 + [
    "......####......",
    ".....######.....",
    "....##.##.##....",
    "...##..##..##...",
    "..##...##...##..",
    ".##....##....##.",
    "##.....##.....##",
    "................",
]
TRILOBITE = [
    "................",
    ".....######.....",
    "...##########...",
    "..###o####o###..",
    "..############..",
    "...##########...",
    "...#o######o#...",
    "....########....",
    "....#o####o#....",
    "....########....",
    ".....#o##o#.....",
    ".....######.....",
    "......#oo#......",
    "......####......",
    ".......##.......",
    "................",
]
FISH = [  # pointing right
    "................",
    "................",
    "................",
    ".....#...#......",
    "#.....#...#..##.",
    "##.....#...#####",
    ".##############o",
    ".###############",
    "##.....#...#####",
    "#.....#...#..##.",
    ".....#...#......",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def ammonite():
    rows = []
    cx = cy = 7.5
    for y in range(16):
        row = ""
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            if r > 6.9:
                row += "."
                continue
            theta = math.atan2(dy, dx) % (2 * math.pi)
            groove = False
            for k in range(4):  # the spiral groove between whorls
                rs = 0.6 + (theta + 2 * math.pi * k) * 6.2 / (2.6 * 2 * math.pi)
                if abs(r - rs) < 0.55:
                    groove = True
            rib = r > 4.4 and (int(theta / (2 * math.pi) * 22) % 2 == 0) and r < 6.2
            row += "o" if groove or (rib and r > 5.6) else "#"
        rows.append(row)
    return rows


def fern():
    g = [["."] * 16 for _ in range(16)]
    for y in range(1, 16):
        g[y][7] = "#"
        if y > 9:
            g[y][8] = "#"
    for y in range(2, 14, 2):
        length = min(6, 1 + (y - 1) // 2)
        for k in range(1, length + 1):
            yy = y - k // 3
            if 0 <= yy < 16:
                if 7 - k >= 0:
                    g[yy][7 - k] = "#"
                if 8 + k < 16:
                    g[yy][8 + k] = "#"
    return ["".join(r) for r in g]


# ---- drawing helpers for the curved and organic shapes ----------------------------------------------


def blank():
    return [["."] * 16 for _ in range(16)]


def rows(g):
    return ["".join(r) for r in g]


def dot(g, x, y, ch="#"):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = ch


def disc(g, cx, cy, r, ch="#"):
    for y in range(16):
        for x in range(16):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                g[y][x] = ch


def line(g, x0, y0, x1, y1, r=0.0, ch="#"):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for i in range(n + 1):
        t = i / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        if r <= 0:
            dot(g, x, y, ch)
        else:
            disc(g, x, y, r, ch)


def chain(path, thick=1.0, knob=1.6, spacing=3.6, spines=False):
    """A string of vertebrae along a path given as a list of points (in pixels)."""
    g = blank()
    pts = []
    for (x0, y0), (x1, y1) in zip(path, path[1:]):
        n = int(math.hypot(x1 - x0, y1 - y0) * 4) + 1
        for i in range(n):
            t = i / n
            pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
    pts.append(path[-1])
    run = 0.0
    last = pts[0]
    for i, (x, y) in enumerate(pts):
        run += math.hypot(x - last[0], y - last[1])
        last = (x, y)
        disc(g, x, y, thick)
        if run % spacing < 0.25 or i == 0:
            disc(g, x, y, knob)
            if spines:
                dot(g, x, y - knob - 1)
                dot(g, x, y - knob - 2)
    return rows(g)


def bezier(p0, p1, p2, n=12):
    out = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        out.append((x, y))
    return out


# ---- more skulls, all side-on and pointing right -----------------------------------------------------

HORN_SKULL_SIDE = [  # a frill at the back, a long brow horn and a nose horn, a hooked beak
    "#...............",
    "##........##....",
    "###.....###.....",
    "####...###......",
    ".####.###.......",
    ".#########......",
    "..###oo####..#..",
    "..###oo#####.##.",
    "..#############.",
    "..##############",
    "...############.",
    "...##.#.#.#####.",
    "....##########..",
    "................",
    "................",
    "................",
]
HORN_SKULL_FRONT = [
    ".##############.",
    "##............##",
    "#..##......##..#",
    "#..##......##..#",
    "#..###....###..#",
    "#...########...#",
    "##.##oo##oo##.##",
    ".#.##oo##oo##.#.",
    "...####..####...",
    "....###..###....",
    ".....######.....",
    "......####......",
    "......####......",
    ".......##.......",
    "................",
    "................",
]
CREST_SKULL_SIDE = [  # a long tube crest sweeping back, a duck bill
    "................",
    "##..............",
    ".###............",
    "..####..........",
    "...#######......",
    "...#########....",
    "..###oo######...",
    "..###oo#########",
    "..##############",
    "..########.#.#..",
    "..###...........",
    "..##########....",
    "...########.....",
    "................",
    "................",
    "................",
]
BEAK_SKULL_SIDE = [  # a flier's long toothy beak and back crest
    "................",
    "................",
    "#...............",
    ".##.............",
    "..####..........",
    "..######........",
    "..##oo#######...",
    "..##oo##########",
    "..##############",
    "..###.#.#.#.#...",
    "...############.",
    "....######......",
    "................",
    "................",
    "................",
    "................",
]
FANG_SKULL_SIDE = [  # a sabre-toothed cat
    "................",
    "................",
    "................",
    "....######......",
    "...#########....",
    "..###oo#######..",
    "..###oo########.",
    "..#############.",
    "..############..",
    "..##.....###....",
    "..##......##....",
    "..###.....##....",
    "...#####..##....",
    "...........#....",
    "................",
    "................",
]
TUSK_SIDE = [  # hanging from the skull above, sweeping down and forward, then curling up
    "###.............",
    "####............",
    ".####..........#",
    "..####........##",
    "...####......##.",
    "....#####...###.",
    ".....#########..",
    "......#######...",
    "........###.....",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
TUSK_END = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......####......",
    ".....######.....",
    ".....##oo##.....",
    ".....######.....",
    "......####......",
    "................",
    "................",
    "................",
    "................",
]
PLATES_SIDE = [  # a back plate or sail spine standing over the backbone
    "......##........",
    ".....####.......",
    "....######......",
    "...###o####.....",
    "..####o#####....",
    "##.###.###.###.#",
    "################",
    "################",
    "##.###.###.###.#",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
PLATES_FRONT = [
    "......####......",
    "......####......",
    ".......##.......",
] + VERTEBRA_END[3:]
SHELL_SIDE = [  # a turtle's domed shell, side-on
    "................",
    "................",
    "................",
    ".....######.....",
    "...##o####o##...",
    "..###o####o###..",
    ".####o####o####.",
    ".oooooooooooooo.",
    "####o#####o#####",
    "####o#####o#####",
    "################",
    "#.#.#.#.#.#.#.#.",
    "................",
    "................",
    "................",
    "................",
]


def shell_top():
    g = blank()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 7.2, (y - 7.5) / 7.8
            r = math.hypot(dx, dy)
            if r > 1:
                continue
            groove = 0.78 < r < 0.86 or (r < 0.78 and (y in (4, 8, 11) and abs(x - 7.5) < 3.2 or x in (4, 11) and 2 < y < 13))
            g[y][x] = "o" if groove else "#"
    return rows(g)


SHELL_TOP = shell_top()
TAIL_TIP_SIDE = [  # the last of the tail, tapering to a point on the left
    "................",
    "................",
    "................",
    "................",
    "................",
    "........#...#...",
    "..##############",
    "......#.###.###.",
    "..........#...#.",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
CLAW_SIDE = [  # a hunter's foot with its big sickle claw
    ".....######.....",
    ".....######.....",
    "......####......",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    "......####......",
    ".....######.....",
    "....##.##.###...",
    "...##..##..###..",
    "..##...##...##..",
    ".##....##...##..",
    "##.....##..##...",
    "...........#....",
]
FLIPPER_SIDE = [  # a paddle of finger bones
    ".....######.....",
    "......####......",
    ".......##.......",
    ".......##.......",
    "......####......",
    ".....######.....",
    ".....#.##.#.....",
    "....#.#..#.#....",
    "....#.#..#.#....",
    "....#.#..#.#....",
    ".....#.#..#.#...",
    ".....#.#..#.#...",
    "......#.#..#.#..",
    ".......#.#..#...",
    "........#.......",
    "................",
]

# neck pieces: from the backbone (middle of the left edge) up to the top right corner, then
# corner to corner, then from the bottom left corner to the middle of the right edge (the skull)
NECK_BASE_SIDE = chain(bezier((-1, 6.5), (9, 6.5), (16.5, -0.5)))
NECK_SIDE = chain([(-0.5, 16.5), (16.5, -0.5)])
NECK_TOP_SIDE = chain(bezier((-0.5, 16.5), (5, 6.5), (16.5, 6.5)))


def wing():
    g = blank()
    line(g, -1, 16, 16, -1, 0.75)          # the long finger bone
    for x0, top in ((3, 12), (7, 8), (11, 4)):  # the struts of the wing
        line(g, x0, top + 1, x0 - 1, 15)
    disc(g, 1, 14, 1.3)                    # the joint
    return rows(g)


WING_SIDE = wing()

# ---- more small fossils ------------------------------------------------------------------------------


def scallop():
    g = blank()
    cx, cy = 7.5, 13.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, cy - y
            r = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dx, dy))
            edge = 10.2 + 0.6 * math.cos(math.radians(a) * 13)
            if r <= edge and abs(a) <= 64 and y <= 13:
                g[y][x] = "#"
    for ang in (-46, -23, 0, 23, 46):  # the ribs fanning out from the hinge
        t = math.radians(ang)
        line(g, cx + math.sin(t) * 4.2, cy - math.cos(t) * 4.2, cx + math.sin(t) * 9.6, cy - math.cos(t) * 9.6, 0, "o")
    for x in range(3, 13):  # the hinge and its ears
        g[13][x] = "#"
        if 4 <= x <= 11:
            g[14][x] = "#"
    return rows(g)


SNAIL = [
    "................",
    "......##........",
    ".....####.......",
    ".....#o###......",
    "....##oo###.....",
    "....###oo###....",
    "...####ooo###...",
    "...######ooo##..",
    "..##o#######oo#.",
    "..##oo#########.",
    ".###ooo########.",
    ".####ooo######..",
    "..#####oooo###..",
    "...##########...",
    ".....######.....",
    "................",
]
CRAB = [
    "................",
    "..##........##..",
    ".#..#......#..#.",
    ".##.#......#.##.",
    "..###......###..",
    "...##.####.##...",
    "....########....",
    "...##########...",
    "..###o####o###..",
    "..############..",
    "...##########...",
    "..#.#.####.#.#..",
    ".#..#......#..#.",
    "#...#......#...#",
    "................",
    "................",
]


def starfish():
    g = blank()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx) + math.pi / 2
            arm = 0.5 + 0.5 * math.cos(5 * a)
            if r <= 2.6 + 5.2 * arm ** 1.1:
                g[y][x] = "o" if r < 1.0 or (r > 2.6 and arm > 0.9 and round(r) % 2 == 0) else "#"
    return rows(g)


def urchin():
    g = blank()
    disc(g, 7.5, 7.5, 6.6)
    for k in range(5):
        a = math.radians(k * 72 - 90)
        for rr in (2.4, 3.9, 5.4):
            for side in (-1, 1):
                px = 7.5 + math.cos(a) * rr - math.sin(a) * side * 0.9
                py = 7.5 + math.sin(a) * rr + math.cos(a) * side * 0.9
                dot(g, px, py, "o")
    disc(g, 7.5, 7.5, 1.0, "o")
    return rows(g)


def crinoid():
    g = blank()
    for y in range(7, 16):  # the stem, in segments
        g[y][7] = g[y][8] = "#"
        if y % 2 == 0:
            g[y][6] = g[y][9] = "#"
    disc(g, 7.5, 6.5, 1.6)
    for ang in (-48, -24, 0, 24, 48):  # the arms of the crown, each feathered
        a = math.radians(ang)
        x1, y1 = 7.5 + math.sin(a) * 7.5, 6.5 - math.cos(a) * 7.0
        line(g, 7.5, 6.5, x1, y1)
        for k in (2, 4, 6):
            px, py = 7.5 + math.sin(a) * k, 6.5 - math.cos(a) * k
            dot(g, px + math.cos(a) * 1.2, py + math.sin(a) * 1.2)
    return rows(g)


DRAGONFLY = [
    "................",
    ".......##.......",
    "......####......",
    ".#####.##.#####.",
    "#.#.#.####.#.#.#",
    "#.....####.....#",
    ".#####.##.#####.",
    "..####.##.####..",
    ".#...#.##.#...#.",
    "..###..##..###..",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    "........#.......",
    "................",
]


def feather():
    g = blank()
    x0, y0, x1, y1 = 3.0, 14.5, 12.5, 1.0
    length = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    px, py = -uy, ux
    for i in range(2, int(length)):
        sx, sy = x0 + ux * i, y0 + uy * i
        w = 3.6 * math.sin(math.pi * min(1.0, i / length * 1.15)) + 0.6
        if i % 4 == 3:
            continue  # a split in the vane
        for side in (1, -1):
            line(g, sx, sy, sx + (px * side) * w + ux * 1.2, sy + (py * side) * w + uy * 1.2)
    line(g, x0 - 1, y0 + 1, x1, y1)
    return rows(g)


def leaf():
    g = blank()
    for y in range(16):
        for x in range(16):
            u = ((x - 7.5) + (7.5 - y)) / math.sqrt(2)   # along the leaf
            v = ((x - 7.5) - (7.5 - y)) / math.sqrt(2)   # across it
            if (u / 8.2) ** 2 + (v / 3.9) ** 2 <= 1:
                vein = abs(v) < 0.6 or (abs(abs(v) - (u + 6) * 0.7 % 3.2) < 0.45 and abs(v) > 1)
                g[y][x] = "o" if vein else "#"
    line(g, 0, 15, 2, 13)
    return rows(g)


SHARK_TOOTH = [
    "................",
    ".......##.......",
    ".......##.......",
    "......####......",
    "......####......",
    ".....######.....",
    ".....######.....",
    "....########....",
    "....########....",
    "...##########...",
    "...##########...",
    "..############..",
    "..oooooooooooo..",
    ".##############.",
    "..############..",
    "................",
]
FOOTPRINT = [  # three toes pointing up (north), pressed into the rock
    "................",
    "................",
    "................",
    "..o....o....o...",
    "..oo...o...oo...",
    "...oo..o..oo....",
    "...oo.ooo.oo....",
    "....oooooooo....",
    "....ooooooo.....",
    ".....ooooo......",
    ".....ooooo......",
    "......ooo.......",
    "................",
    "................",
    "................",
    "................",
]

NEW_PICTURES = {
    "horn_skull_side": HORN_SKULL_SIDE, "horn_skull_front": HORN_SKULL_FRONT, "crest_skull_side": CREST_SKULL_SIDE,
    "beak_skull_side": BEAK_SKULL_SIDE, "fang_skull_side": FANG_SKULL_SIDE, "tusk_side": TUSK_SIDE, "tusk_end": TUSK_END,
    "plates_side": PLATES_SIDE, "plates_front": PLATES_FRONT, "shell_side": SHELL_SIDE, "shell_top": SHELL_TOP,
    "tail_tip_side": TAIL_TIP_SIDE, "claw_side": CLAW_SIDE, "flipper_side": FLIPPER_SIDE,
    "neck_base_side": NECK_BASE_SIDE, "neck_side": NECK_SIDE, "neck_top_side": NECK_TOP_SIDE, "wing_side": WING_SIDE,
    "scallop": scallop(), "snail": SNAIL, "crab": CRAB, "starfish": starfish(), "urchin": urchin(), "crinoid": crinoid(),
    "dragonfly": DRAGONFLY, "feather": feather(), "leaf": leaf(), "shark_tooth": SHARK_TOOTH, "footprint": FOOTPRINT,
}


def side_piece(side, front="vertebra_end", back="vertebra_end", top="spine_top", down=None):
    return {"dir": True, "faces": {"north": front, "south": back, "east": side, "west": side + "|MIRROR",
                                   "up": top, "down": down or top}}


def all_faces(pic, mirror_back=False):
    f = {d: pic for d in ("north", "south", "east", "west", "up", "down")}
    if mirror_back:
        f["south"] = f["west"] = pic + "|MIRROR"
    return {"dir": False, "faces": f}


NEW_PIECES = {
    "horn_skull": side_piece("horn_skull_side", front="horn_skull_front", top="skull_top", down="skull_top|FLIP"),
    "crest_skull": side_piece("crest_skull_side", front="skull_front", top="skull_top", down="skull_top|FLIP"),
    "beak_skull": side_piece("beak_skull_side", front="skull_front", top="skull_top", down="skull_top|FLIP"),
    "fang_skull": side_piece("fang_skull_side", front="skull_front", top="skull_top", down="skull_top|FLIP"),
    "neck_base": side_piece("neck_base_side"),
    "neck": side_piece("neck_side"),
    "neck_top": side_piece("neck_top_side"),
    "plates": side_piece("plates_side", front="plates_front", back="plates_front"),
    "shell": side_piece("shell_side", front="ribs_front", back="ribs_front", top="shell_top"),
    "tail_tip": side_piece("tail_tip_side", front="tail_end", back="tail_end", top="tail_top"),
    "wing": side_piece("wing_side", front="limb_end", back="limb_end", top="tail_top"),
    "tusk": side_piece("tusk_side", front="tusk_end", back="tusk_end", top="tusk_side"),
    "claw": side_piece("claw_side", front="foot_side", back="foot_side", top="limb_end"),
    "flipper": side_piece("flipper_side", front="limb_side", back="limb_side", top="limb_end"),
    "scallop": all_faces("scallop"),
    "snail": all_faces("snail", True),
    "crab": all_faces("crab"),
    "starfish": all_faces("starfish"),
    "urchin": all_faces("urchin"),
    "crinoid": all_faces("crinoid"),
    "dragonfly": all_faces("dragonfly"),
    "feather": all_faces("feather", True),
    "leaf": all_faces("leaf", True),
    "shark_tooth": all_faces("shark_tooth"),
    "footprint": {"dir": True, "print": True, "faces": {"up": "footprint"}},
}
NEW_PIECE_NAMES = {
    "horn_skull": "Horned Skull", "crest_skull": "Crested Skull", "beak_skull": "Beaked Skull", "fang_skull": "Sabre-Tooth Skull",
    "neck_base": "Neck Base", "neck": "Neck", "neck_top": "Upper Neck", "plates": "Back Plate", "shell": "Shell",
    "tail_tip": "Tail Tip", "wing": "Wing", "tusk": "Tusk", "claw": "Claw", "flipper": "Flipper",
    "scallop": "Scallop", "snail": "Snail", "crab": "Crab", "starfish": "Starfish", "urchin": "Sea Urchin",
    "crinoid": "Sea Lily", "dragonfly": "Dragonfly", "feather": "Feather", "leaf": "Leaf", "shark_tooth": "Shark Tooth",
    "footprint": "Footprint",
}
SMALL = ["ammonite", "trilobite", "fish", "fern", "scallop", "snail", "crab", "starfish", "urchin", "crinoid",
         "dragonfly", "feather", "leaf", "shark_tooth"]

# ---- the skeletons the world can hold, drawn side-on with the head to the right -----------------------
# K skull, H horned skull, C crested skull, B beaked skull, G sabre-tooth skull, S spine, R ribs,
# Q back plates, D shell, T tail, t tail tip, N neck base, n neck, m upper neck, W wing, U tusk,
# L leg, F foot, X claw, P flipper. The row holding the backbone is marked with a '>' in front.

SKELETONS = {
    "sauropod": [
        "|                mK",
        "|               n  ",
        "|              n   ",
        ">tTTTTTSRRRRRSN    ",
        "|       L   L      ",
        "|       L   L      ",
        "|       F   F      ",
    ],
    "tyrant": [
        ">tTTTTSRRSK",
        "|      L   ",
        "|      L   ",
        "|      F   ",
    ],
    "raptor": [
        ">tTTSRSK",
        "|   L   ",
        "|   X   ",
    ],
    "horned": [
        ">tTTSRRRSH",
        "|   L  L  ",
        "|   F  F  ",
    ],
    "duckbill": [
        "|       mC",
        ">tTTTSRN  ",
        "|    L   ",
        "|    L   ",
        "|    F   ",
    ],
    "plated": [
        ">tTTQQQQQSK",
        "|    L  L  ",
        "|    F  F  ",
    ],
    "sail_back": [
        ">tTTQQQSK",
        "|   L  L ",
        "|   F  F ",
    ],
    "sea_serpent": [
        "|         mK",
        "|        n  ",
        ">tTSRRRSN   ",
        "|   P P    ",
    ],
    "fish_lizard": [
        ">tTTTSRRRSB",
        "|     P P  ",
    ],
    "flier": [
        "|     W ",
        "|    W  ",
        "|   W   ",
        ">tSRRSB ",
        "|  F    ",
    ],
    "sabre_cat": [
        ">tTSRRSG",
        "|  L  L ",
        "|  F  F ",
    ],
    "mammoth": [
        ">tSRRRRSK",
        "|  L  L U",
        "|  L  L  ",
        "|  F  F  ",
    ],
    "turtle": [
        ">tDDDSK",
        "| P  P ",
    ],
    "serpent": [
        ">tTTRRRRRRRRRRSK",
    ],
    "whale": [
        ">tTTTTSRRRRRRSSK",
        "|           P   ",
    ],
    "terror_bird": [
        "|    mB",
        ">tSRN  ",
        "| L   ",
        "| L   ",
        "| X   ",
    ],
    "small_hunter": [
        ">tTSRK",
        "|  X  ",
    ],
    "giant_lizard": [
        ">tTTTSRRSK",
        "|    P P  ",
    ],
}

for name, pic in NEW_PICTURES.items():
    assert len(pic) == 16 and all(len(r) == 16 for r in pic), name
SKULLS = "KHCBG"
for name, sk in SKELETONS.items():
    assert sum(1 for r in sk if r.startswith(">")) == 1, name
    grid = {}
    for y, r in enumerate(sk):
        assert r[0] in ">|", (name, r)
        for x, ch in enumerate(r[1:]):
            assert ch in " KHCBGSRQDTtNnmWULFXP", (name, ch)
            if ch != " ":
                grid[(x, y)] = ch
    for (x, y), ch in grid.items():
        # necks climb corner to corner and end at a skull; wings climb too
        if ch in "Nn":
            assert grid.get((x + 1, y - 1)) in ("n", "m"), (name, ch, x, y)
        if ch == "m":
            assert grid.get((x + 1, y)) in tuple(SKULLS), (name, ch, x, y)
    assert sum(1 for ch in grid.values() if ch in SKULLS) == 1, name


# ---- amber, petrified wood and fossil eggs (whole textures, no rock behind) ---------------------------


def amber(inclusion):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = 0.5 * math.sin((x * 0.7 + y * 0.35) + 2 * math.sin(y * 0.5)) + 0.5
            c = tuple(int(a + (b - a) * n) for a, b in zip((0xC8, 0x6A, 0x0E), (0xF0, 0xA0, 0x2A)))
            if hash01(x, y, 77) > 0.94:
                c = (0xFF, 0xD0, 0x70)
            if x in (0, 15) or y in (0, 15):
                c = shade(c, 0.8)
            img.putpixel((x, y), c + (255,))
    dark = (0x3A, 0x1E, 0x08, 255)
    if inclusion == "mosquito":
        for x, y in [(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 10), (8, 11), (9, 12),
                     (6, 5), (9, 5), (5, 8), (4, 9), (3, 11), (10, 8), (11, 9), (12, 11), (6, 9), (5, 11), (9, 9), (10, 11)]:
            img.putpixel((x, y), dark)
        for x, y in [(5, 6), (4, 6), (3, 7), (10, 6), (11, 6), (12, 7)]:
            img.putpixel((x, y), (0xE8, 0xC8, 0x90, 255))  # wings
    elif inclusion == "beetle":
        for y in range(5, 12):
            for x in range(5, 11):
                if ((x - 7.5) / 2.6) ** 2 + ((y - 8.5) / 3.6) ** 2 <= 1:
                    img.putpixel((x, y), (0x24, 0x34, 0x18, 255) if x != 7 and x != 8 or y < 7 else dark)
        for x, y in [(7, 4), (8, 4), (6, 3), (9, 3), (4, 6), (11, 6), (4, 9), (11, 9), (4, 11), (11, 11)]:
            img.putpixel((x, y), dark)
    return img


PETRIFIED_RINGS = [(0x8E, 0x66, 0x4E), (0xB4, 0x86, 0x5A), (0x74, 0x58, 0x48), (0xC6, 0x9E, 0x76), (0x8C, 0x80, 0x78),
                   (0xA8, 0x5A, 0x3C)]


def petrified_side():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            ridge = (x * 5 + int(3 * math.sin(y * 0.6 + x))) % 16
            base = (0x7A, 0x6E, 0x64) if ridge < 9 else (0x5E, 0x54, 0x4C)
            base = shade(base, 0.9 + 0.2 * hash01(x, y, 3))
            if hash01(x, y, 9) > 0.95:
                base = (0xC8, 0xC2, 0xB8)  # quartz glints
            img.putpixel((x, y), base + (255,))
    return img


def petrified_top():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5) + 0.6 * math.sin(math.atan2(y - 7.5, x - 7.5) * 3)
            if r > 7.2:
                c = (0x5E, 0x54, 0x4C)
            else:
                c = PETRIFIED_RINGS[int(r * 0.9) % len(PETRIFIED_RINGS)]
            img.putpixel((x, y), shade(c, 0.92 + 0.16 * hash01(x, y, 4)) + (255,))
    return img


def fossil_bone_side():
    """Bone turned to stone: the grain of a bone block in pale tan, flecked with mineral, stained here and there."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            streak = (x * 3 + int(1.5 * math.sin(y * 0.5 + x * 1.3))) % 7
            c = (0xD6, 0xC6, 0xA2) if streak < 4 else (0xC2, 0xAE, 0x88)
            if y in (0, 15):
                c = (0xA8, 0x96, 0x74)  # the ends of the bone
            c = shade(c, 0.93 + 0.14 * hash01(x, y, 21))
            if hash01(x, y, 22) > 0.94:
                c = (0x8C, 0x78, 0x5C)  # mineral flecks
            elif hash01(x // 3, y // 4, 23) > 0.82:
                c = _mix(c, (0xB4, 0x84, 0x58), 0.3)  # iron stains
            img.putpixel((x, y), tuple(int(v) for v in c) + (255,))
    return img


def fossil_bone_top():
    """The broken end of a stone bone: a hard pale rim round a spongy middle filled in with rock."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = 3 <= x <= 12 and 3 <= y <= 12 and not ((x in (3, 12)) and (y in (3, 12)))
            if edge:
                c = (0xA8, 0x96, 0x74)
            elif inner:
                c = (0xC4, 0xB0, 0x8A) if hash01(x, y, 24) > 0.4 else (0x98, 0x86, 0x68)  # spongy, filled with stone
            else:
                c = (0xDC, 0xCC, 0xA8)
            img.putpixel((x, y), shade(c, 0.94 + 0.12 * hash01(x, y, 25)) + (255,))
    return img


def egg_texture():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade((0xB8, 0xAC, 0x94), 0.9 + 0.2 * hash01(x, y, 12))
            if hash01(x, y, 13) > 0.86:
                c = (0x7C, 0x6A, 0x56)
            if hash01(x * 3, y, 14) > 0.97:
                c = (0x5C, 0x56, 0x52)  # cracks
            img.putpixel((x, y), c + (255,))
    return img


# ---- the Cambrian: strange sea creatures from the oldest rocks ------------------------------------------

ANOMALOCARIS_HEAD = [  # the front half, pointing right: grasping arms curled under the head, eyes on stalks
    "................",
    "................",
    "...........#.#..",
    "..........#...#.",
    ".#.#.#.#.##.##..",
    "##o##o##o####...",
    "#############oo.",
    "##############..",
    "##############..",
    "#############oo.",
    "##o##o##o####...",
    ".#.#.#.#.##.##..",
    "..........#...#.",
    "...........#.#..",
    "................",
    "................",
]
ANOMALOCARIS_TAIL = [  # the back half: flaps down the sides, a fan of tail blades
    "................",
    "................",
    "................",
    "##..............",
    ".###....#.#.#.#.",
    "..####.#o##o##o#",
    "...#############",
    "....############",
    "....############",
    "...#############",
    "..####.#o##o##o#",
    ".###....#.#.#.#.",
    "##..............",
    "................",
    "................",
    "................",
]
OPABINIA = [  # five eyes on top, a long nozzle ending in a claw
    "................",
    "................",
    "................",
    ".........#.#.#..",
    ".........#####..",
    ".#.#.#.#.######.",
    "#############...",
    "##o##o##o####...",
    "#############...",
    ".#.#.#.#.###....",
    "...........###..",
    ".............##.",
    "..............#.",
    ".............#.#",
    "................",
    "................",
]
HALLUCIGENIA = [  # spines along its back, stilt legs below
    "................",
    "................",
    "................",
    "..#...#...#...#.",
    "...#...#...#...#",
    "....#...#...#...",
    ".##############.",
    "################",
    ".##############.",
    "..#..#..#..#..#.",
    ".#..#..#..#..#..",
    ".#..#..#..#..#..",
    "................",
    "................",
    "................",
    "................",
]


def wiwaxia():
    g = blank()
    for y in range(16):
        for x in range(16):
            if ((x - 7.5) / 6.5) ** 2 + ((y - 9.5) / 4.0) ** 2 <= 1:
                inner = ((x - 7.5) / 5.5) ** 2 + ((y - 9.5) / 3.0) ** 2 <= 1
                g[y][x] = "o" if inner and (x + 2 * y) % 4 == 0 else "#"
    for x0, y0, x1, y1 in ((3, 7, 1, 1), (5.5, 6, 4.5, 0), (8, 6, 8.5, 0), (10.5, 6, 11.5, 0), (13, 7, 15, 1)):
        line(g, x0, y0, x1, y1)
    return rows(g)


def marrella():
    g = blank()
    for y in range(4, 15):  # the body, in segments, with little legs
        g[y][7] = g[y][8] = "#"
        if y % 2 == 0:
            g[y][6] = g[y][9] = "#"
            g[y][5] = g[y][10] = "#" if y > 5 else "."
    disc(g, 7.5, 3.5, 1.8)
    for pts in (bezier((6, 3), (1, 4), (1, 14)), bezier((9, 3), (14, 4), (14, 14)),
                bezier((6, 4), (3, 7), (3, 15)), bezier((9, 4), (12, 7), (12, 15))):
        for (a, b), (c, d) in zip(pts, pts[1:]):
            line(g, a, b, c, d)
    line(g, 6, 2, 3, 0)
    line(g, 9, 2, 12, 0)
    return rows(g)


def pikaia():
    g = blank()
    for x in range(1, 16):
        cy = 7.5 + 0.9 * math.sin(x / 2.5)
        w = 2.0 * math.sin(math.pi * x / 17) + 0.5
        for y in range(16):
            if abs(y - cy) <= w:
                g[y][x] = "o" if x % 3 == 0 and abs(y - cy) < w - 0.3 else "#"
    dot(g, 15, 6)
    dot(g, 15, 9)
    return rows(g)


def jellyfish():
    g = blank()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 6.9:
                continue
            a = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 45
            canal = (a < 5 or a > 40) and r > 1.2
            g[y][x] = "o" if canal or abs(r - 3.6) < 0.45 else "#"
    return rows(g)


def archaeocyath():
    g = blank()
    for y in range(1, 15):
        half = 1.2 + (14 - y) * 0.42
        for x in range(16):
            d = abs(x - 7.5)
            if d <= half:
                hollow = y < 5 and d < half - 2.4
                pore = not hollow and y % 2 == 0 and int(x + y) % 3 == 0 and d < half - 0.5
                g[y][x] = "o" if hollow or pore else "#"
    return rows(g)


def burrows():
    g = blank()
    for k, (y0, amp, ph) in enumerate(((3.0, 1.6, 0.0), (8.0, 2.2, 1.7), (12.5, 1.3, 3.1))):
        pts = [(x, y0 + amp * math.sin(x / 2.2 + ph)) for x in range(-1, 18)]
        for (a, b), (c, d) in zip(pts, pts[1:]):
            line(g, a, b, c, d)
    line(g, 4, 3, 4, 8)   # a U-shaped burrow
    line(g, 11, 4, 11, 9)
    return rows(g)


CAMBRIAN_PICTURES = {
    "anomalocaris_head": ANOMALOCARIS_HEAD, "anomalocaris_tail": ANOMALOCARIS_TAIL, "opabinia": OPABINIA,
    "hallucigenia": HALLUCIGENIA, "wiwaxia": wiwaxia(), "marrella": marrella(), "pikaia": pikaia(),
    "jellyfish": jellyfish(), "archaeocyath": archaeocyath(), "burrows": burrows(),
}
CAMBRIAN = ["anomalocaris_head", "anomalocaris_tail", "opabinia", "hallucigenia", "wiwaxia", "marrella", "pikaia",
            "jellyfish", "archaeocyath", "burrows"]

# ---- the Neolithic: paintings on cave walls, in ochre and charcoal --------------------------------------
# r red ochre, k charcoal, y yellow ochre

PAINTING_AUROCHS = [
    "................",
    "................",
    "..........k...k.",
    "..........kk.kk.",
    "...rrrrrrrrrrrk.",
    "..rrrrrrrrrrrrkk",
    ".rkrrrrrrrrrrrr.",
    ".r.rrrrrrrrrrr..",
    "...rrrrrrrrr....",
    "...k.k....k.k...",
    "...k.k....k.k...",
    "...k.k....k.k...",
    "................",
    "................",
    "................",
    "................",
]
PAINTING_DEER = [
    "................",
    "..........k.k.k.",
    "...........kkk..",
    "..........k.k...",
    "...........kk...",
    "...........kkk..",
    "..kkkkkkkkkkk...",
    ".kkkkkkkkkkk....",
    "..kkkkkkkkk.....",
    "..k.k....k.k....",
    "..k..k...k..k...",
    ".k...k..k...k...",
    "................",
    "................",
    "................",
    "................",
]
PAINTING_HORSE = [
    "................",
    "................",
    "................",
    "...........kk...",
    "..........kkkk..",
    "...yyyyyyykyyyk.",
    "..yyyyyyyyyyyy..",
    ".kyyyyyyyyyyy...",
    ".k.yyyyyyyyy....",
    "...k.k...k.k....",
    "...k.k...k.k....",
    "..k..k..k..k....",
    "................",
    "................",
    "................",
    "................",
]
PAINTING_MAMMOTH = [
    "................",
    "....kkkkkk......",
    "..kkrrrrrrkk....",
    ".krrrrrrrrrrk...",
    ".krrrrrrrrrrrk..",
    ".krrrrrrrrrrrrk.",
    ".krrrrrrrrrrrrk.",
    "..krrrrrrrrrrrk.",
    "..krrrrrrrrrk.k.",
    "..krk..krk..k.k.",
    "..krk..krk.y..k.",
    "..kkk..kkk..yyk.",
    "................",
    "................",
    "................",
    "................",
]


def hunters():
    g = blank()
    for x in (3, 8, 13):
        g[2][x] = "r"
        for y in range(3, 8):
            g[y][x] = "r"
        g[4][x - 1] = g[4][x + 1] = "r"
        g[8][x - 1] = g[9][x - 1] = "r"
        g[8][x + 1] = g[9][x + 1] = "r"
        line(g, x + 1, 9, min(15, x + 3), 1, 0, "r")   # a spear
    line(g, 0, 13, 15, 13, 0, "k")                       # the ground
    return rows(g)


def hands():
    g = blank()
    hand = ["r.r.r..", "r.r.r.r", "r.r.r.r", "rrrrrrr", "rrrrrrr", "rrrrrr.", ".rrrr..", ".rrrr.."]
    for dy, row in enumerate(hand):
        for dx, ch in enumerate(row):
            if ch == "r":
                g[1 + dy][2 + dx] = "r"
                if 7 + dy < 16 and 8 + dx < 16:
                    g[7 + dy][8 + dx] = "k"
    for x, y in ((12, 2), (14, 4), (13, 6), (2, 12), (4, 14)):
        g[y][x] = "y"
    return rows(g)


def spiral():
    g = blank()
    pts = []
    for i in range(140):
        t = i / 140 * 3.2 * 2 * math.pi
        r = 0.4 + t * 6.4 / (3.2 * 2 * math.pi)
        pts.append((7.5 + r * math.cos(t), 7.5 + r * math.sin(t)))
    for x, y in pts:
        dot(g, x, y, "y")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (2, 8), (13, 8)):
        g[y][x] = "r"
    return rows(g)


PAINTING_PICTURES = {
    "painting_aurochs": PAINTING_AUROCHS, "painting_deer": PAINTING_DEER, "painting_horse": PAINTING_HORSE,
    "painting_mammoth": PAINTING_MAMMOTH, "painting_hunters": hunters(), "painting_hands": hands(),
    "painting_spiral": spiral(),
}
PAINTINGS = list(PAINTING_PICTURES)
PAINT = {"r": (0xA8, 0x3E, 0x26), "k": (0x2A, 0x24, 0x22), "y": (0xC8, 0x8E, 0x3A)}

NEW_PICTURES.update(CAMBRIAN_PICTURES)
NEW_PICTURES.update(PAINTING_PICTURES)
NEW_PIECES.update({
    "anomalocaris_head": side_piece("anomalocaris_head", front="tail_end", back="tail_end", top="anomalocaris_head"),
    "anomalocaris_tail": side_piece("anomalocaris_tail", front="tail_end", back="tail_end", top="anomalocaris_tail"),
    "opabinia": all_faces("opabinia", True),
    "hallucigenia": all_faces("hallucigenia", True),
    "wiwaxia": all_faces("wiwaxia"),
    "marrella": all_faces("marrella"),
    "pikaia": all_faces("pikaia", True),
    "jellyfish": all_faces("jellyfish"),
    "archaeocyath": all_faces("archaeocyath"),
    "burrows": all_faces("burrows", True),
})
for _p in PAINTINGS:
    NEW_PIECES[_p] = {"dir": True, "painting": True, "faces": {"north": _p}}
NEW_PIECE_NAMES.update({
    "anomalocaris_head": "Anomalocaris Head", "anomalocaris_tail": "Anomalocaris Tail", "opabinia": "Opabinia",
    "hallucigenia": "Hallucigenia", "wiwaxia": "Wiwaxia", "marrella": "Marrella", "pikaia": "Pikaia",
    "jellyfish": "Jellyfish", "archaeocyath": "Archaeocyath", "burrows": "Burrow",
    "painting_aurochs": "Aurochs Painting", "painting_deer": "Deer Painting", "painting_horse": "Horse Painting",
    "painting_mammoth": "Mammoth Painting", "painting_hunters": "Hunters Painting", "painting_hands": "Handprint Painting",
    "painting_spiral": "Spiral Painting",
})

# ---- Neolithic items: a knapped flint hand axe, a bone flute, a lump of ochre ---------------------------

ITEMS = {
    "flint_hand_axe": ({"a": (0x9A, 0x96, 0xA0), "b": (0x5E, 0x5A, 0x64), "c": (0x36, 0x33, 0x3A)}, [
        "................",
        ".........aab....",
        "........abbbc...",
        ".......abbcbc...",
        "......abcbbbc...",
        ".....abbbcbbc...",
        "....abcbbbbc....",
        "....abbbcbbc....",
        "...abbcbbbc.....",
        "...abbbbcbc.....",
        "...abcbbbc......",
        "....abbbc.......",
        ".....acc........",
        "................",
        "................",
        "................",
    ]),
    "bone_flute": ({"a": (0xF0, 0xE8, 0xD4), "b": (0xD6, 0xCB, 0xAE), "c": (0xA8, 0x9A, 0x7A), "k": (0x3A, 0x30, 0x28)}, [
        "................",
        "................",
        "............aa..",
        "...........abba.",
        "..........abbc..",
        ".........abkc...",
        "........abbc....",
        ".......abkc.....",
        "......abbc......",
        ".....abkc.......",
        "....abbc........",
        "...abbc.........",
        "..aabc..........",
        "..abc...........",
        "................",
        "................",
    ]),
    "ochre": ({"a": (0xD2, 0x7A, 0x4A), "b": (0xA8, 0x50, 0x2E), "c": (0x7A, 0x34, 0x20)}, [
        "................",
        "................",
        "................",
        "................",
        "......aab.......",
        ".....abbbb......",
        "....abbbbbc.....",
        "...abbcbbbbc....",
        "...abbbbbcccc...",
        "....bbcbbbcc....",
        ".....cccccc.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]),
}
ITEM_NAMES = {"flint_hand_axe": "Flint Hand Axe", "bone_flute": "Bone Flute", "ochre": "Ochre"}


PICTURES = {
    "skull_side": SKULL_SIDE, "skull_front": SKULL_FRONT, "skull_top": SKULL_TOP,
    "vertebra_end": VERTEBRA_END, "spine_side": SPINE_SIDE, "spine_top": SPINE_TOP,
    "ribs_side": RIBS_SIDE, "ribs_front": RIBS_FRONT, "ribs_top": RIBS_TOP,
    "tail_side": TAIL_SIDE, "tail_end": TAIL_END, "tail_top": TAIL_TOP,
    "limb_side": LIMB_SIDE, "limb_end": LIMB_END, "foot_side": FOOT_SIDE,
    "ammonite": ammonite(), "trilobite": TRILOBITE, "fish": FISH, "fern": fern(),
}

PICTURES.update(NEW_PICTURES)
for name, pic in PICTURES.items():
    assert len(pic) == 16 and all(len(r) == 16 for r in pic), name

# which picture goes on which face; MIRROR flips it left-right, FLIP upside down
# directional pieces point north: the east face shows the side view pointing right
PIECES = {
    "skull": {"dir": True, "faces": {"north": "skull_front", "south": "vertebra_end", "east": "skull_side",
                                      "west": "skull_side|MIRROR", "up": "skull_top", "down": "skull_top|FLIP"}},
    "spine": {"dir": True, "faces": {"north": "vertebra_end", "south": "vertebra_end", "east": "spine_side",
                                      "west": "spine_side|MIRROR", "up": "spine_top", "down": "spine_top"}},
    "ribs": {"dir": True, "faces": {"north": "ribs_front", "south": "ribs_front", "east": "ribs_side",
                                     "west": "ribs_side|MIRROR", "up": "spine_top", "down": "ribs_top"}},
    "tail": {"dir": True, "faces": {"north": "tail_end", "south": "tail_end", "east": "tail_side",
                                     "west": "tail_side|MIRROR", "up": "tail_top", "down": "tail_top"}},
    "limb": {"dir": False, "faces": {d: "limb_side" for d in ("north", "south", "east", "west")} | {"up": "limb_end", "down": "limb_end"}},
    "foot": {"dir": False, "faces": {d: "foot_side" for d in ("north", "south", "east", "west")} | {"up": "limb_end", "down": "limb_end"}},
    "ammonite": {"dir": False, "faces": {d: "ammonite" for d in ("north", "south", "east", "west", "up", "down")}},
    "trilobite": {"dir": False, "faces": {d: "trilobite" for d in ("north", "south", "east", "west", "up", "down")}},
    "fish": {"dir": False, "faces": {"north": "fish", "south": "fish|MIRROR", "east": "fish", "west": "fish|MIRROR",
                                     "up": "fish", "down": "fish"}},
    "fern": {"dir": False, "faces": {d: "fern" for d in ("north", "south", "east", "west", "up", "down")}},
}
PIECES.update(NEW_PIECES)
for _p in ("limb", "foot"):
    PIECES[_p]["dir"] = False
PIECE_NAMES = {
    "skull": "Skull", "spine": "Spine", "ribs": "Ribs", "tail": "Tail", "limb": "Leg Bone", "foot": "Foot",
    "ammonite": "Ammonite", "trilobite": "Trilobite", "fish": "Fish", "fern": "Fern",
}
PIECE_NAMES.update(NEW_PIECE_NAMES)
# the order pieces are listed in (and registered in): skeleton first, then small fossils, then the print
ORDER = ["skull", "horn_skull", "crest_skull", "beak_skull", "fang_skull", "neck_base", "neck", "neck_top", "spine", "ribs",
         "plates", "shell", "tail", "tail_tip", "wing", "tusk", "limb", "foot", "claw", "flipper"] + SMALL + CAMBRIAN \
    + ["footprint"] + PAINTINGS
assert sorted(ORDER) == sorted(PIECES), set(ORDER) ^ set(PIECES)

# which fossils each of the formation rocks holds (the older rocks hold everything)
_MARINE = ["skull", "beak_skull", "neck_base", "neck", "neck_top", "spine", "ribs", "tail", "tail_tip", "flipper"]
HOST_PIECES = {
    "shale": CAMBRIAN + ["trilobite", "ammonite", "fish", "fern", "leaf", "dragonfly", "crinoid", "feather", "footprint"] + _MARINE,
    "limestone": ["ammonite", "trilobite", "scallop", "snail", "crinoid", "urchin", "starfish", "crab", "shark_tooth", "fish",
                  "jellyfish", "dragonfly", "feather", "archaeocyath", "footprint"] + _MARINE
                 + ["wing", "limb", "foot", "shell", "claw"] + PAINTINGS,
    "chalk": ["urchin", "shark_tooth", "ammonite", "scallop", "starfish", "crab", "fish", "snail", "footprint"] + _MARINE
             + ["wing", "shell", "limb", "foot"] + PAINTINGS,
    "permafrost": ["skull", "fang_skull", "beak_skull", "tusk", "neck_base", "neck", "neck_top", "spine", "ribs", "tail",
                   "tail_tip", "limb", "foot", "claw", "feather", "leaf", "snail"],
}
for _h, _ps in HOST_PIECES.items():
    assert all(p in PIECES for p in _ps), (_h, [p for p in _ps if p not in PIECES])


def host_pieces(host):
    key = HOSTS[host].get("pieces")
    return [p for p in ORDER if key is None or p in HOST_PIECES[key]]


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def hash01(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def render(pic, host, seed):
    if any(ch in "rky" for row in pic for ch in row):
        return render_painting(pic, seed)
    light, mid, dark = HOSTS[host]["bone"]
    hollow = HOSTS[host]["hollow"]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def bone(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and pic[y][x] == "#"

    for y in range(16):
        for x in range(16):
            ch = pic[y][x]
            if ch == "#":
                if not bone(x, y - 1) or not bone(x - 1, y):
                    c = light
                elif not bone(x, y + 1) or not bone(x + 1, y):
                    c = dark
                else:
                    c = mid
                n = hash01(x, y, seed)
                if n > 0.88:
                    c = shade(c, 0.9)
                elif n < 0.08:
                    c = shade(c, 1.06)
                img.putpixel((x, y), c + (255,))
            elif ch == "o" or bone(x - 1, y - 1) and (bone(x - 1, y) or bone(x, y - 1)):
                # hollows, and the shadow the bone casts down and to the right into the rock
                img.putpixel((x, y), hollow + (255,))
    return img


def render_painting(pic, seed):
    """Flat ochre and charcoal, worn away in places."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            ch = pic[y][x]
            if ch in PAINT and hash01(x, y, seed) < 0.9:
                img.putpixel((x, y), shade(PAINT[ch], 0.88 + 0.22 * hash01(x, y, seed + 1)) + (255,))
    return img


def items(lang):
    tex = os.path.join(ASSETS, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    for name, (pal, pic) in ITEMS.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(pic):
            assert len(row) == 16, name
            for x, ch in enumerate(row):
                if ch in pal:
                    img.putpixel((x, y), pal[ch] + (255,))
        img.save(os.path.join(tex, name + ".png"))
        parent = "minecraft:item/handheld" if name == "flint_hand_axe" else "minecraft:item/generated"
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": parent, "textures": {"layer0": f"{MODID}:item/{name}"}})
        lang[f"item.{MODID}.{name}"] = ITEM_NAMES[name]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def simple_block(bid, lang_name, model, lang, loot=None, variants=None):
    if model is not None:
        write_json(os.path.join(ASSETS, "models", "block", bid + ".json"), model)
    write_json(os.path.join(ASSETS, "models", "item", bid + ".json"), {"parent": f"{MODID}:block/{bid}"})
    write_json(os.path.join(ASSETS, "blockstates", bid + ".json"),
               {"variants": variants or {"": {"model": f"{MODID}:block/{bid}"}}})
    write_json(os.path.join(DATA, MODID, "loot_tables", "blocks", bid + ".json"), loot or {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MODID}:{bid}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    lang[f"block.{MODID}.{bid}"] = lang_name


def extras(lang, tex_dir):
    """Amber with things caught in it, petrified wood, and fossil eggs."""
    ids = []
    for inc, name in (("", "Amber"), ("mosquito", "Amber with a Mosquito"), ("beetle", "Amber with a Beetle")):
        bid = "amber" + ("_" + inc if inc else "")
        amber(inc).save(os.path.join(tex_dir, bid + ".png"))
        simple_block(bid, name, {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MODID}:block/fossil/{bid}"}}, lang)
        ids.append(bid)
    petrified_side().save(os.path.join(tex_dir, "petrified_log.png"))
    petrified_top().save(os.path.join(tex_dir, "petrified_log_top.png"))
    simple_block("petrified_log", "Petrified Log", {"parent": "minecraft:block/cube_column", "textures": {
        "side": f"{MODID}:block/fossil/petrified_log", "end": f"{MODID}:block/fossil/petrified_log_top"}}, lang,
        variants={"axis=y": {"model": f"{MODID}:block/petrified_log"},
                  "axis=x": {"model": f"{MODID}:block/petrified_log", "x": 90, "y": 90},
                  "axis=z": {"model": f"{MODID}:block/petrified_log", "x": 90}})
    simple_block("petrified_wood", "Petrified Wood", {"parent": "minecraft:block/cube_all", "textures": {
        "all": f"{MODID}:block/fossil/petrified_log"}}, lang)
    ids += ["petrified_log", "petrified_wood"]
    fossil_bone_side().save(os.path.join(tex_dir, "fossil_bone.png"))
    fossil_bone_top().save(os.path.join(tex_dir, "fossil_bone_top.png"))
    simple_block("fossil_bone", "Fossilised Bone Block", {"parent": "minecraft:block/cube_column", "textures": {
        "side": f"{MODID}:block/fossil/fossil_bone", "end": f"{MODID}:block/fossil/fossil_bone_top"}}, lang,
        variants={"axis=y": {"model": f"{MODID}:block/fossil_bone"},
                  "axis=x": {"model": f"{MODID}:block/fossil_bone", "x": 90, "y": 90},
                  "axis=z": {"model": f"{MODID}:block/fossil_bone", "x": 90}})
    ids.append("fossil_bone")

    egg_texture().save(os.path.join(tex_dir, "fossil_egg.png"))
    # one, two or three eggs lying in a clutch
    spots = [[(8, 8, 1.0)], [(5, 6, 0.9), (10.5, 10, 1.0)], [(5, 5, 0.85), (11, 6, 0.9), (8, 11, 1.0)]]
    variants = {}
    for n, eggs in enumerate(spots, 1):
        elements = []
        for cx, cz, sc in eggs:
            for (w, y0, y1) in ((2.4, 0, 1.2), (3.2, 1.2, 4.8), (2.6, 4.8, 7.2), (1.6, 7.2, 8.4)):
                w *= sc
                elements.append({"from": [round(cx - w, 2), round(y0 * sc, 2), round(cz - w, 2)],
                                 "to": [round(cx + w, 2), round(y1 * sc, 2), round(cz + w, 2)],
                                 "faces": {d: {"texture": "#egg"} for d in ("north", "south", "east", "west", "up", "down")}})
        model = {"parent": "minecraft:block/block", "textures": {"egg": f"{MODID}:block/fossil/fossil_egg",
                                                                 "particle": f"{MODID}:block/fossil/fossil_egg"},
                 "elements": elements}
        name = "fossil_egg" if n == 1 else f"fossil_egg_{n}"
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
        variants[f"eggs={n}"] = {"model": f"{MODID}:block/{name}"}
    loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:item", "name": f"{MODID}:fossil_egg",
        "functions": [{"function": "minecraft:set_count", "count": n, "conditions": [{
            "condition": "minecraft:block_state_property", "block": f"{MODID}:fossil_egg", "properties": {"eggs": str(n)}}]}
            for n in (2, 3)]}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    simple_block("fossil_egg", "Fossil Egg", None, lang, loot=loot, variants=variants)
    ids.append("fossil_egg")
    return ids


def write_java():
    """The piece list and the skeleton shapes, for the Java side."""
    java = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "java", "com", "harold", "corpsedecay", "block")
    lines = ["package com.harold.corpsedecay.block;", "",
             "/** Generated by tools/gen_fossils.py: every fossil piece, and the skeletons they make. */",
             "public final class FossilShapes {", "    private FossilShapes() {", "    }", "",
             "    /** id, kind (0 skeleton bone, 1 small fossil, 2 footprint, 3 Cambrian, 4 cave painting), points along the body */",
             "    public static final Object[][] PIECES = {"]
    for p in ORDER:
        kind = 4 if PIECES[p].get("painting") else 2 if PIECES[p].get("print") else 3 if p in CAMBRIAN else 1 if p in SMALL else 0
        lines.append(f'        {{"{p}", {kind}, {"true" if PIECES[p]["dir"] else "false"}}},')
    lines.append("    };")
    lines.append("")
    lines.append("    /** rock id, then the pieces fossils in it come as (rocks not listed have every piece) */")
    lines.append("    public static final String[][] HOST_PIECES = {")
    for h in HOSTS:
        if HOSTS[h].get("pieces"):
            lines.append(f'        {{"{h}", ' + ", ".join(f'"{p}"' for p in host_pieces(h)) + "},")
    lines.append("    };")
    lines.append("")
    lines.append("    /** letter in a skeleton drawing to piece id */")
    lines.append("    public static final String[][] LETTERS = {")
    letters = {"K": "skull", "H": "horn_skull", "C": "crest_skull", "B": "beak_skull", "G": "fang_skull", "S": "spine",
               "R": "ribs", "Q": "plates", "D": "shell", "T": "tail", "t": "tail_tip", "N": "neck_base", "n": "neck",
               "m": "neck_top", "W": "wing", "U": "tusk", "L": "limb", "F": "foot", "X": "claw", "P": "flipper"}
    for k, v in letters.items():
        lines.append(f'        {{"{k}", "{v}"}},')
    lines.append("    };")
    lines.append("")
    lines.append("    /** name, then rows drawn side-on with the head to the right; '>' marks the backbone row */")
    lines.append("    public static final String[][] SKELETONS = {")
    for name, rows_ in SKELETONS.items():
        lines.append(f'        {{"{name}",')
        for r in rows_:
            lines.append(f'            "{r}",')
        lines.append("        },")
    lines.append("    };")
    lines.append("}")
    with open(os.path.join(java, "FossilShapes.java"), "w") as f:
        f.write("\n".join(lines) + "\n")


# ---- rocks of the formations ------------------------------------------------------------------------


def _noise_img(fn):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), tuple(max(0, min(255, int(v))) for v in fn(x, y)) + (255,))
    return img


def _mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def rock_textures():
    """Textures for the new rocks: (side, top) pictures for each."""
    def shale_side(x, y):
        band = (y * 7 + int(2 * math.sin(x * 0.7 + y))) % 5
        c = (0x46, 0x46, 0x4C) if band < 3 else (0x36, 0x36, 0x3C)
        if y % 5 == 0 and hash01(x, y, 1) > 0.3:
            c = (0x5A, 0x5A, 0x60)  # a paler lamina
        return shade(c, 0.9 + 0.2 * hash01(x, y, 2))

    def shale_top(x, y):
        return shade((0x40, 0x40, 0x46), 0.85 + 0.3 * hash01(x, y, 3))

    def limestone_side(x, y):
        c = (0xC6, 0xBE, 0xA8)
        if y in (5, 11) and hash01(x, y, 4) > 0.25:
            c = (0xA8, 0xA0, 0x8C)  # bedding
        n = hash01(x, y, 5)
        if n > 0.93:
            c = (0xE6, 0xDE, 0xC8)  # shell bits
        elif n < 0.05:
            c = (0x96, 0x8E, 0x7A)
        return shade(c, 0.94 + 0.12 * hash01(x, y, 6))

    def limestone_top(x, y):
        c = (0xC2, 0xBA, 0xA4)
        if hash01(x, y, 7) > 0.92:
            c = (0xE2, 0xDA, 0xC4)
        return shade(c, 0.94 + 0.12 * hash01(x, y, 8))

    def chalk(x, y):
        c = (0xEC, 0xEA, 0xE2)
        if hash01(x, y, 9) > 0.9:
            c = (0xD8, 0xD6, 0xCC)
        return shade(c, 0.96 + 0.06 * hash01(x, y, 10))

    def chalk_flint(x, y):
        for cx, cy, r in ((4, 5, 2.6), (11.5, 9, 3.0), (6, 12.5, 1.8)):
            d = math.hypot(x - cx, (y - cy) * 1.3)
            if d < r:
                return shade((0x2C, 0x2E, 0x36), 0.85 + 0.35 * hash01(x, y, 11)) if d < r - 0.8 else (0x9A, 0x98, 0x94)
        return chalk(x, y)

    def coal_seam(x, y):
        c = (0x1E, 0x1C, 0x1C) if (y + int(hash01(x // 4, y, 12) * 2)) % 4 else (0x2A, 0x28, 0x28)
        if hash01(x, y, 13) > 0.95:
            c = (0x6A, 0x66, 0x70)  # glints
        return shade(c, 0.9 + 0.2 * hash01(x, y, 14))

    def lignite(x, y):
        streak = (x * 3 + int(3 * math.sin(y * 0.4))) % 7
        c = (0x3E, 0x2C, 0x1E) if streak < 4 else (0x2C, 0x20, 0x16)
        return shade(c, 0.9 + 0.2 * hash01(x, y, 15))

    def permafrost_side(x, y):
        c = (0x50, 0x42, 0x36)
        lens = (y in (3, 4) and 2 < x < 10) or (y == 9 and x > 6) or (y in (13, 14) and x < 7)
        if lens:
            c = (0xB4, 0xD0, 0xE2) if hash01(x, y, 16) > 0.15 else (0xE2, 0xF0, 0xF8)
        elif hash01(x, y, 17) > 0.9:
            c = (0x6C, 0x5C, 0x4C)
        return shade(c, 0.92 + 0.16 * hash01(x, y, 18))

    def permafrost_top(x, y):
        c = (0x4C, 0x40, 0x36)
        if hash01(x, y, 19) > 0.85:
            c = (0xA8, 0xC4, 0xD6)
        return shade(c, 0.92 + 0.16 * hash01(x, y, 20))

    def tar(x, y):
        c = (0x16, 0x14, 0x16)
        r = math.hypot(x - 5, y - 5)
        if 1.2 < r < 2.4 and x < 6 and y < 6:
            c = (0x48, 0x44, 0x4E)  # a glossy shine
        elif hash01(x, y, 21) > 0.94:
            c = (0x30, 0x2C, 0x30)
        return c

    return {
        "shale": (shale_side, shale_top), "limestone": (limestone_side, limestone_top), "chalk": (chalk, chalk),
        "chalk_flint": (chalk_flint, chalk), "coal_seam": (coal_seam, coal_seam), "lignite": (lignite, lignite),
        "permafrost": (permafrost_side, permafrost_top), "tar": (tar, tar),
    }


ROCK_NAMES = {"shale": "Shale", "limestone": "Limestone", "chalk": "Chalk", "chalk_flint": "Chalk with Flint",
              "coal_seam": "Coal Seam", "lignite": "Lignite", "permafrost": "Permafrost", "tar": "Tar"}


def ore_loot(bid, item, lo, hi):
    """Drops the block with silk touch, otherwise the item (more with fortune)."""
    silk = {"condition": "minecraft:match_tool", "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch",
                                                                                   "levels": {"min": 1}}]}}
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": f"{MODID}:{bid}", "conditions": [silk]},
        {"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]}]}]}]}


def rocks(lang):
    tex = os.path.join(ASSETS, "textures", "block")
    ids = []
    for bid, (side, top) in rock_textures().items():
        _noise_img(side).save(os.path.join(tex, bid + ".png"))
        _noise_img(top).save(os.path.join(tex, bid + "_top.png"))
        model = {"parent": "minecraft:block/cube_column", "textures": {"side": f"{MODID}:block/{bid}", "end": f"{MODID}:block/{bid}_top"}}
        loot = None
        if bid == "chalk_flint":
            loot = ore_loot(bid, "minecraft:flint", 1, 2)
        elif bid == "coal_seam":
            loot = ore_loot(bid, "minecraft:coal", 1, 2)
        simple_block(bid, ROCK_NAMES[bid], model, lang, loot=loot)
        ids.append(bid)
    return ids



def main():
    tex_dir = os.path.join(ASSETS, "textures", "block", "fossil")
    os.makedirs(tex_dir, exist_ok=True)
    for f in os.listdir(tex_dir):
        os.remove(os.path.join(tex_dir, f))
    for host in HOSTS:
        needed = {PIECES[p]["faces"][d].split("|")[0] for p in host_pieces(host) for d in PIECES[p]["faces"]}
        for i, (name, pic) in enumerate(PICTURES.items()):
            if name in needed:
                render(pic, host, i * 31 + len(host)).save(os.path.join(tex_dir, f"{host}_{name}.png"))

    ids = []
    lang = {}
    for host, h in HOSTS.items():
        side, top, bottom = h["tex"]
        for piece in host_pieces(host):
            spec = PIECES[piece]
            bid = f"{host}_{piece}_fossil"
            ids.append(bid)
            lang[f"block.{MODID}.{bid}"] = f"{h['name']} {PIECE_NAMES[piece]} Fossil"
            textures = {"particle": side, "side": side, "top": top, "bottom": bottom}
            base_faces = {}
            over_faces = {}
            for d in ("north", "south", "east", "west", "up", "down"):
                base_faces[d] = {"texture": "#top" if d == "up" else "#bottom" if d == "down" else "#side", "cullface": d}
                if d not in spec["faces"]:
                    continue
                pic, *flags = spec["faces"][d].split("|")
                key = f"o_{pic}"
                textures[key] = f"{MODID}:block/fossil/{host}_{pic}"
                uv = [0, 0, 16, 16]
                if "MIRROR" in flags:
                    uv = [16, 0, 0, 16]
                if "FLIP" in flags:
                    uv = [0, 16, 16, 0]
                over_faces[d] = {"texture": "#" + key, "uv": uv, "cullface": d}
            model = {
                "parent": "minecraft:block/block",
                "render_type": "minecraft:cutout",
                "textures": textures,
                "elements": [
                    {"from": [0, 0, 0], "to": [16, 16, 16], "faces": base_faces},
                    {"from": [0, 0, 0], "to": [16, 16, 16], "faces": over_faces},
                ],
            }
            write_json(os.path.join(ASSETS, "models", "block", bid + ".json"), model)
            write_json(os.path.join(ASSETS, "models", "item", bid + ".json"), {"parent": f"{MODID}:block/{bid}"})
            if spec["dir"]:
                variants = {f"facing={d}": {"model": f"{MODID}:block/{bid}", "y": y}
                            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
                for v in variants.values():
                    if v["y"] == 0:
                        del v["y"]
            else:
                variants = {"": {"model": f"{MODID}:block/{bid}"}}
            write_json(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": variants})
            write_json(os.path.join(DATA, MODID, "loot_tables", "blocks", bid + ".json"), {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": f"{MODID}:{bid}"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })

    ids += rocks(lang)
    ids += extras(lang, tex_dir)
    items(lang)
    write_java()
    write_json(os.path.join(DATA, "minecraft", "tags", "blocks", "mineable", "pickaxe.json"),
               {"replace": False, "values": [f"{MODID}:{b}" for b in ids]})

    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path) as f:
        existing = json.load(f)
    existing = {k: v for k, v in existing.items() if not k.startswith(f"block.{MODID}.") and not k.startswith(f"item.{MODID}.")}
    existing.update(lang)
    write_json(lang_path, existing)

    # a contact sheet to look at
    rock = {"stone": (0x7D, 0x7D, 0x7D), "deepslate": (0x4A, 0x4A, 0x4F), "sandstone": (0xD8, 0xCB, 0x9B),
            "red_sandstone": (0xBA, 0x63, 0x24), "terracotta": (0x98, 0x5E, 0x43), "shale": (0x42, 0x42, 0x48),
            "limestone": (0xC4, 0xBC, 0xA6), "chalk": (0xEA, 0xE8, 0xE0), "permafrost": (0x4E, 0x40, 0x36)}
    sheet = Image.new("RGBA", (len(PICTURES) * 18, len(HOSTS) * 18), (40, 40, 40, 255))
    for j, host in enumerate(HOSTS):
        for i, name in enumerate(PICTURES):
            tile = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    tile.putpixel((x, y), shade(rock[host], 0.85 + 0.3 * hash01(x, y, 5)) + (255,))
            f = os.path.join(tex_dir, f"{host}_{name}.png")
            if os.path.exists(f):
                tile.alpha_composite(Image.open(f))
            sheet.paste(tile, (i * 18 + 1, j * 18 + 1))
    out = os.environ.get("FOSSIL_SHEET")
    if out:
        sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save(out)
    print(len(ids), "fossil blocks")


if __name__ == "__main__":
    main()

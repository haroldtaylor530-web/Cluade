"""Generates the placeholder pixel art for Flint & Fire.

Run: python3 make_art.py <path to src/main/resources/assets/realisticsurvival>
"""
import math
import os
import random
import sys

from PIL import Image

OUT = sys.argv[1]
random.seed(7)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(img, rel):
    p = os.path.join(OUT, "textures", rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def ascii_img(rows, pal, noise=None, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:size]):
            if ch in pal:
                c = pal[ch]
                if noise and ch in noise:
                    d = random.randint(-noise[ch], noise[ch])
                    c = tuple(max(0, min(255, v + d)) for v in c[:3]) + (c[3],)
                img.putpixel((x, y), c)
    return img


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


# ---------------------------------------------------------------- palettes
FLINT = {"k": hexc("1c1c22"), "g": hexc("454550"), "G": hexc("676774"), "W": hexc("a4a4b2")}
WOOD = {"h": hexc("6b4a2b"), "H": hexc("8f653b"), "d": hexc("4a321c")}
BIND = {"b": hexc("b39d62"), "B": hexc("d4c084")}
FUR = {"D": hexc("4a3622"), "F": hexc("85633f"), "L": hexc("b38d63")}

# ---------------------------------------------------------------- items
items = {}

items["flint_shard"] = ascii_img([
    "................",
    "................",
    "..........k.....",
    ".........kWk....",
    "........kGWgk...",
    ".......kGGGgk...",
    "......kgGWGgk...",
    ".....kgGGGgk....",
    "....kgGGGgk.....",
    "...kgGGggk......",
    "...kGGggk.......",
    "..kGGgk.........",
    "..kGgk..........",
    "..kkk...........",
    "................",
    "................"], FLINT)

items["plant_fibre"] = ascii_img([
    "................",
    ".....g......g...",
    ".....g.....g....",
    "....gG....gG....",
    "....g.G..gG.....",
    "...gG..G.G......",
    "...g...GG...g...",
    "..gG...Gg..gG...",
    "..g...gG.g.g....",
    "..g..gG..gGg....",
    "..g.gG...gg.....",
    "...gG....g......",
    "...g....gG......",
    "...g...gG.......",
    "..yy..yy........",
    "................"], {"g": hexc("4c7628"), "G": hexc("7aa83e"), "y": hexc("8a7a3a")})

items["fibre_string"] = ascii_img([
    "................",
    "................",
    ".....tttttt.....",
    "....tTttttTt....",
    "...tT......Tt...",
    "...t..tttt..t...",
    "..tT.tT..Tt.Tt..",
    "..t..t....t..t..",
    "..t..t....t..t..",
    "..tT.tT..Tt.Tt..",
    "...t..tttt..t...",
    "...tT......Tt...",
    "....tTttttTt.T..",
    ".....tttttt...T.",
    "...............T",
    "................"], {"t": hexc("8c7a4a"), "T": hexc("bba86c")})

items["flint_knife"] = ascii_img([
    "................",
    "............kk..",
    "...........kWWk.",
    "..........kGWk..",
    ".........kGGk...",
    "........kGgk....",
    ".......kGgk.....",
    "......bkgk......",
    ".....bBbk.......",
    "....hbbb........",
    "...hHb..........",
    "..hHh...........",
    ".hHh............",
    ".hh.............",
    "................",
    "................"], {**FLINT, **WOOD, **BIND})

items["flint_hatchet"] = ascii_img([
    "................",
    "....kkkk........",
    "...kGWWGk.......",
    "..kGWGGGgk......",
    "..kGGGGgbbk.....",
    "...kgggbBbk.....",
    "....kkkbbbHh....",
    ".........hH.....",
    "........hH......",
    ".......hH.......",
    "......hH........",
    ".....hH.........",
    "....hH..........",
    "...hH...........",
    "...h............",
    "................"], {**FLINT, **WOOD, **BIND})

items["flint_pickaxe"] = ascii_img([
    "................",
    "....kkkkkk......",
    "...kGWWGGGk.....",
    "..kGgkkkbBGk....",
    "..kk....bbkGk...",
    ".......hbb.kGk..",
    "......hH....kGk.",
    ".....hH......kk.",
    "....hH..........",
    "...hH...........",
    "..hH............",
    ".hH.............",
    ".h..............",
    "................",
    "................",
    "................"], {**FLINT, **WOOD, **BIND})


def spear_icon(tip_dark, tip_mid, tip_light):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(0, 11):
        y = 15 - x
        img.putpixel((x, y), WOOD["H"])
        if x + 1 < 16:
            img.putpixel((x + 1, y), WOOD["h"])
    for x in (9, 10):
        img.putpixel((x, 15 - x), BIND["B"])
        img.putpixel((x + 1, 15 - x), BIND["b"])
    tip = [(11, 4), (12, 4), (11, 3), (12, 3), (13, 3), (12, 2), (13, 2), (14, 2), (13, 1), (14, 1), (15, 0), (14, 0), (15, 1),
           (10, 4), (11, 5), (12, 5), (10, 5)]
    for (x, y) in tip:
        img.putpixel((x, y), tip_mid)
    for (x, y) in [(12, 3), (13, 2), (14, 1)]:
        img.putpixel((x, y), tip_light)
    for (x, y) in [(10, 5), (12, 5), (11, 5), (10, 4)]:
        img.putpixel((x, y), tip_dark)
    return img


items["flint_spear_icon"] = spear_icon(FLINT["k"], FLINT["G"], FLINT["W"])
items["iron_spear_icon"] = spear_icon(hexc("5a5a5a"), hexc("c8c8c8"), hexc("ffffff"))
items["diamond_spear_icon"] = spear_icon(hexc("0e6b63"), hexc("4aedd9"), hexc("d4fff9"))


def cup(body, rim, water=None):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark = shade(body, 0.7)
    for y in range(5, 14):
        inset = (y - 5) // 4
        for x in range(3 + inset, 12 - inset):
            c = body
            if x == 3 + inset or x == 11 - inset:
                c = dark
            if x == 4 + inset:
                c = shade(body, 1.15)
            img.putpixel((x, y), c)
    for x in range(3, 12):
        img.putpixel((x, 4), rim)
    if water:
        for x in range(4, 11):
            img.putpixel((x, 5), water)
        img.putpixel((5, 5), shade(water, 1.3))
    for y in (6, 7, 8, 9, 10):
        img.putpixel((12 if y in (6, 10) else 13, y), dark)
    for x in range(3, 12):
        img.putpixel((x, 14), dark)
    return img


CLAY_RAW = hexc("a1a6b4")
CLAY_FIRED = hexc("b0603a")
items["unfired_clay_cup"] = cup(CLAY_RAW, shade(CLAY_RAW, 1.1))
items["clay_cup"] = cup(CLAY_FIRED, shade(CLAY_FIRED, 1.15))
items["dirty_water_cup"] = cup(CLAY_FIRED, shade(CLAY_FIRED, 1.15), hexc("5d6e3a"))
items["clean_water_cup"] = cup(CLAY_FIRED, shade(CLAY_FIRED, 1.15), hexc("3f76e4"))

LEATHER = {"l": hexc("4e3219"), "L": hexc("8b5a2b"), "M": hexc("a8703a"), "n": hexc("2e1f10"), "s": hexc("3a2614")}
items["waterskin_full"] = ascii_img([
    "................",
    "......ss........",
    ".....s..s.......",
    ".....s...s......",
    "......nn..s.....",
    ".....lnnl..s....",
    "....lLMLLl..s...",
    "...lLMLLLLl..s..",
    "..lLMLLLLLLl.s..",
    "..lLMLLLLLLl.s..",
    "..lLLLLLLLLls...",
    "..lLLLLLLLLl....",
    "...lLLLLLLl.....",
    "....llllll......",
    "................",
    "................"], LEATHER)
items["waterskin"] = ascii_img([
    "................",
    "......ss........",
    ".....s..s.......",
    ".....s...s......",
    "......nn..s.....",
    "......nn...s....",
    ".....lLLl...s...",
    ".....lLLl....s..",
    "....lLLLLl...s..",
    "....lLLLLl..s...",
    "...lLLLLLLl.....",
    "...lLLLLLLLl....",
    "...llLLLLLll....",
    ".....llllll.....",
    "................",
    "................"], {**LEATHER, "L": hexc("7a4e25")})

items["bandage"] = ascii_img([
    "................",
    "................",
    "................",
    "....wwwwwww.....",
    "...wWWWWWWWw....",
    "..wWwwwwwwwWw...",
    "..wWwSSSSSwWw...",
    "..wWwSoooSwWw...",
    "..wWwSSSSSwWw...",
    "..wWwwwwwwwWw...",
    "...wWWWWWWWwwww.",
    "....wwwwwwwwWWw.",
    "............www.",
    "................",
    "................",
    "................"], {"w": hexc("cfcabd"), "W": hexc("f2efe6"), "S": hexc("b5ae9c"), "o": hexc("8a8372")})

splint = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for x in range(1, 14):
    y = 14 - x
    for (dx, dy, c) in [(0, 0, WOOD["H"]), (1, 0, WOOD["h"]), (2, 2, WOOD["H"]), (3, 2, WOOD["h"])]:
        if 0 <= x + dx < 16 and 0 <= y + dy < 16:
            splint.putpixel((x + dx, y + dy), c)
for (cx, cy) in [(4, 10), (9, 5)]:
    for k in range(-1, 4):
        if 0 <= cx + k < 16 and 0 <= cy + k < 16:
            splint.putpixel((cx + k, cy + k - 1), BIND["B"] if k % 2 else BIND["b"])
items["splint"] = splint

items["fur_pelt"] = ascii_img([
    "................",
    "...DD......DD...",
    "...DFD....DFD...",
    "....DFDDDDFD....",
    "...DFFLFFFFFD...",
    "..DFFFFFLFFFFD..",
    "..DFLFFFFFFLFD..",
    "..DFFFFLFFFFFD..",
    "..DFFFFFFFLFFD..",
    "..DFFLFFFFFFFD..",
    "...DFFFFFLFFD...",
    "....DFFFFFFD....",
    "...DFD.DD.DFD...",
    "...DD......DD...",
    "................",
    "................"], FUR, noise={"F": 14, "L": 10})
items["fur_hat"] = ascii_img([
    "................",
    "................",
    ".....DDDDDD.....",
    "....DFFLFFFD....",
    "...DFFFFFLFFD...",
    "...DFLFFFFFFD...",
    "..DLLLLLLLLLLD..",
    "..DLLLLLLLLLLD..",
    "..DLD......DLD..",
    "..DLD......DLD..",
    "..DLD......DLD..",
    "..DDD......DDD..",
    "................",
    "................",
    "................",
    "................"], FUR, noise={"F": 14, "L": 10})
items["fur_coat"] = ascii_img([
    "................",
    "...DDD....DDD...",
    "..DLLLDDDDLLLD..",
    ".DFFFFLLLLFFFFD.",
    ".DFFFFFLLFFFFFD.",
    ".DFFDFFLLFFDFFD.",
    ".DFFDFFLLFFDFFD.",
    ".DLLDFFLLFFDLLD.",
    ".DDDDFFLLFFDDDD.",
    "....DFFLLFFD....",
    "....DFFLLFFD....",
    "....DLLLLLLD....",
    "....DDDDDDDD....",
    "................",
    "................",
    "................"], FUR, noise={"F": 14, "L": 10})
items["fur_trousers"] = ascii_img([
    "................",
    "...DDDDDDDDDD...",
    "...DLLLLLLLLD...",
    "...DFFFFFFFFD...",
    "...DFFFDDFFFD...",
    "...DFFD..DFFD...",
    "...DFFD..DFFD...",
    "...DFFD..DFFD...",
    "...DFFD..DFFD...",
    "...DFFD..DFFD...",
    "...DLLD..DLLD...",
    "...DDDD..DDDD...",
    "................",
    "................",
    "................",
    "................"], FUR, noise={"F": 14, "L": 10})
items["fur_boots"] = ascii_img([
    "................",
    "................",
    "................",
    "..DDDD....DDDD..",
    "..DLLD....DLLD..",
    "..DFFD....DFFD..",
    "..DFFD....DFFD..",
    "..DFFD....DFFD..",
    "..DFFDD...DFFDD.",
    ".DFFFFD..DFFFFD.",
    ".DDDDDD..DDDDDD.",
    "................",
    "................",
    "................",
    "................",
    "................"], FUR, noise={"F": 14, "L": 10})

for name, img in items.items():
    save(img, f"item/{name}.png")

# ---------------------------------------------------------------- spear model texture
# 16x16 sheet: rows 0-1 shaft wood, rows 2-3 binding, rows 4-7 tip (flint/iron/diamond in separate files)
def spear_sheet(tip_dark, tip_mid, tip_light):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(0, 4):
            img.putpixel((x, y), WOOD["H"] if (x + y) % 5 else WOOD["h"])
        for y in range(4, 8):
            img.putpixel((x, y), BIND["B"] if (x + y) % 2 else BIND["b"])
        for y in range(8, 16):
            c = tip_mid
            if y == 8 or x == 0:
                c = tip_light
            if y == 15 or x == 15:
                c = tip_dark
            img.putpixel((x, y), c)
    return img


save(spear_sheet(FLINT["k"], FLINT["G"], FLINT["W"]), "item/flint_spear.png")
save(spear_sheet(hexc("5a5a5a"), hexc("c8c8c8"), hexc("ffffff")), "item/iron_spear.png")
save(spear_sheet(hexc("0e6b63"), hexc("4aedd9"), hexc("d4fff9")), "item/diamond_spear.png")

# ---------------------------------------------------------------- HUD sprites (9x9)
DROP = [
    "....x....",
    "...xBx...",
    "..xBBBx..",
    "..xBWBx..",
    ".xBWBBBx.",
    ".xBBBBBx.",
    ".xBBBBDx.",
    "..xBBDx..",
    "...xxx...",
]
blue = {"x": hexc("10204a"), "B": hexc("3f76e4"), "W": hexc("b8d4ff"), "D": hexc("2a52a8")}
empty = {"x": hexc("10204a"), "B": hexc("2b2b2b"), "W": hexc("3a3a3a"), "D": hexc("222222")}
save(ascii_img(DROP, blue, size=9), "gui/sprites/hud/thirst_full.png")
save(ascii_img(DROP, empty, size=9), "gui/sprites/hud/thirst_empty.png")
half = ascii_img(DROP, blue, size=9)
for y in range(9):
    for x in range(5, 9):
        if half.getpixel((x, y))[3] and DROP[y][x] != "x":
            half.putpixel((x, y), (0, 0, 0, 0))
save(half, "gui/sprites/hud/thirst_half.png")

save(ascii_img([
    "....x....",
    ".x..x..x.",
    "..x.x.x..",
    "...xxx...",
    "xxxxWxxxx",
    "...xxx...",
    "..x.x.x..",
    ".x..x..x.",
    "....x....",
], {"x": hexc("8fd3ff"), "W": hexc("ffffff")}, size=9), "gui/sprites/hud/temp_cold.png")
save(ascii_img([
    "...kkk...",
    "...kWk...",
    "...kWk...",
    "...kGk...",
    "...kGk...",
    "..kGGGk..",
    "..kGGGk..",
    "...kkk...",
    ".........",
], {"k": hexc("1a1a1a"), "W": hexc("e0e0e0"), "G": hexc("4ccc5a")}, size=9), "gui/sprites/hud/temp_normal.png")
save(ascii_img([
    "....o....",
    "...oo....",
    "...oYo...",
    "..oYYo.o.",
    "..oYYYoo.",
    ".oYWWYYo.",
    ".oYWWWYo.",
    "..oYWYo..",
    "...ooo...",
], {"o": hexc("d6461a"), "Y": hexc("ff9a1a"), "W": hexc("ffe680")}, size=9), "gui/sprites/hud/temp_hot.png")

# ---------------------------------------------------------------- effect icons (18x18)
def effect(rows, pal):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    small = ascii_img(rows, pal, size=16)
    img.paste(small, (1, 1), small)
    return img


save(effect([
    "................",
    ".......k........",
    "......kRk.......",
    "......kRk.......",
    ".....kRRRk......",
    ".....kRWRk......",
    "....kRWRRRk.....",
    "....kRWRRRk.....",
    "...kRRRRRRRk....",
    "...kRRRRRRDk....",
    "...kRRRRRRDk....",
    "....kRRRRDk.....",
    ".....kkkkk......",
    "..........kRk...",
    "...........k....",
    "................"], {"k": hexc("3a0000"), "R": hexc("c41818"), "W": hexc("ff8080"), "D": hexc("8a0e0e")}), "mob_effect/bleeding.png")
save(effect([
    "................",
    "......kkkk......",
    "....kkGGGGkk....",
    "...kGGGGGGGGk...",
    "..kGGkGGGGkGGk..",
    "..kGGGGGGGGGGk..",
    "..kGGGGGGGGGGk..",
    "..kGGkkkkkkGGk..",
    "..kGkGGGGGGkGk..",
    "...kGGGGGGGGk...",
    "....kkGGGGkk....",
    "......kkkk......",
    "................",
    "...y..y...y.....",
    "....y..y.y......",
    "................"], {"k": hexc("2a3010"), "G": hexc("8aa83a"), "y": hexc("6b7a2a")}), "mob_effect/dysentery.png")
BONE = {"k": hexc("4a4030"), "W": hexc("efe8d4"), "S": hexc("c8bea0"), "r": hexc("c41818")}
save(effect([
    "................",
    "..kk............",
    ".kWWk...........",
    ".kWWWk..........",
    "..kWWSk.........",
    "...kSWSk........",
    "....kSWSk.......",
    ".....kSWr.......",
    "........rkk.....",
    ".........kSWk...",
    "..........kSWk..",
    "...........kWWk.",
    "...........kWWWk",
    "............kWWk",
    ".............kk.",
    "................"], BONE), "mob_effect/broken_leg.png")
save(effect([
    "................",
    "..kk.......h....",
    ".kWWk.....h.....",
    ".kWWWk...h..h...",
    "..kWWSk.h..h....",
    "...kSWSbbbh.....",
    "....kbbbSkh.....",
    "...h.kSWSbbb....",
    "..h...bbbSk.....",
    ".h..h..kSWSk....",
    "...h....kSWSk...",
    "..h......kSWWk..",
    ".h........kWWWk.",
    "...........kWWk.",
    "............kk..",
    "................"], {**BONE, "h": hexc("8f653b"), "b": hexc("d4c084")}), "mob_effect/splinted.png")
save(effect([
    "................",
    ".......w........",
    ".....w.w.w......",
    "......www.......",
    "...w...w...w....",
    "....w..w..w.....",
    ".w...wWWWw...w..",
    "..wwwwWBWwwwww..",
    ".w...wWWWw...w..",
    "....w..w..w.....",
    "...w...w...w....",
    "......www.......",
    ".....w.w.w......",
    ".......w........",
    "................",
    "................"], {"w": hexc("9ad8ff"), "W": hexc("ffffff"), "B": hexc("5aa0e0")}), "mob_effect/hypothermia.png")
save(effect([
    "................",
    ".......o........",
    "..o....o....o...",
    "...o...o...o....",
    "......YYY.......",
    ".....YWWWY......",
    "....YWWWWWY.....",
    "oo..YWWWWWY..oo.",
    "....YWWWWWY.....",
    ".....YWWWY......",
    "......YYY.......",
    "...o...o...o....",
    "..o....o....o...",
    ".......o........",
    "................",
    "................"], {"o": hexc("ff8a1a"), "Y": hexc("ffb21a"), "W": hexc("fff0a0")}), "mob_effect/heatstroke.png")

# ---------------------------------------------------------------- fur armour layers (64x32)
def fur_layer(regions):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for (x0, y0, x1, y1) in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                r = random.random()
                c = FUR["F"] if r < 0.6 else FUR["L"] if r < 0.85 else FUR["D"]
                d = random.randint(-10, 10)
                img.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,))
    return img


# (fur armour layers are made from the vanilla leather layout; see README)
if False:
  # humanoid: head (0,0)-(32,16), body (16,16)-(40,32), arms (40,16)-(56,32), legs/boots (0,16)-(16,32)
  save(fur_layer([(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)]), "entity/equipment/humanoid/fur.png")
# leggings: body (waist) and legs
  save(fur_layer([(16, 16, 40, 32), (0, 16, 16, 32)]), "entity/equipment/humanoid_leggings/fur.png")
print("art done")

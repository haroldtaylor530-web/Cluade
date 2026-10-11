"""Bow drill icon and the campfire menu background.

Run: python3 tools/make_fire_art.py   (from the repo root)
"""
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")
random.seed(11)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ascii_img(rows, pal, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:size]):
            if ch in pal:
                img.putpixel((x, y), pal[ch])
    return img


# ---------------------------------------------------------------- bow drill: a bent bow with a cord, a spindle and a hearth board
bow = ascii_img([
    "                ",
    "  bB            ",
    "  bBc           ",
    "   bBc          ",
    "    bBc     s   ",
    "     bBc   sS   ",
    "      bBc sS    ",
    "       bBsS     ",
    "        sSBc    ",
    "       sS bBc   ",
    "      sS   bBc  ",
    "   hhsShhhhhbBh ",
    "  hHHHHHHHHHHHHh",
    "  hddddddddddddh",
    "   hhhhhhhhhhhh ",
    "                ",
], {
    "b": hexc("5c3a1e"), "B": hexc("9a6a3a"), "c": hexc("d8cfb0"),
    "s": hexc("6e4a2a"), "S": hexc("b08a5a"),
    "h": hexc("4a3220"), "H": hexc("a07848"), "d": hexc("7a5634"),
})
os.makedirs(os.path.join(T, "item"), exist_ok=True)
bow.save(os.path.join(T, "item", "bow_drill.png"))

# ---------------------------------------------------------------- campfire menu, vanilla container style, 176x176
W, H = 176, 176
img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
BG, HI, SH, OUT = hexc("c6c6c6"), hexc("ffffff"), hexc("555555"), hexc("000000")
# rounded black outline
d.rectangle([1, 0, W - 2, H - 1], fill=OUT)
d.rectangle([0, 1, W - 1, H - 2], fill=OUT)
d.rectangle([1, 1, W - 2, H - 2], fill=BG)
# bevel
d.rectangle([1, 1, W - 4, 2], fill=HI)
d.rectangle([1, 1, 2, H - 4], fill=HI)
d.rectangle([3, H - 3, W - 2, H - 2], fill=SH)
d.rectangle([W - 3, 3, W - 2, H - 2], fill=SH)
img.putpixel((1, 1), BG)
img.putpixel((W - 2, H - 2), BG)
img.putpixel((W - 3, 2), BG)
img.putpixel((2, H - 3), BG)


def slot(x, y):
    # 18x18: dark top/left, white bottom/right, grey inside
    d.rectangle([x, y, x + 17, y + 17], fill=hexc("8b8b8b"))
    d.rectangle([x, y, x + 16, y], fill=hexc("373737"))
    d.rectangle([x, y, x, y + 16], fill=hexc("373737"))
    d.rectangle([x + 1, y + 17, x + 17, y + 17], fill=HI)
    d.rectangle([x + 17, y + 1, x + 17, y + 17], fill=HI)


slot(7, 16)   # kindling
slot(7, 52)   # fuel
for r in range(3):
    for c in range(9):
        slot(7 + c * 18, 93 + r * 18)
for c in range(9):
    slot(7 + c * 18, 151)

# a faint flame outline between the slots, like the furnace
flame = [
    "      #       ",
    "     ##       ",
    "     ###      ",
    "    ####      ",
    "    #####  #  ",
    "   ######  ## ",
    "   ####### ## ",
    "  ############",
    "  ############",
    "  ############",
    "   ########## ",
    "   ########## ",
    "    ########  ",
    "     ######   ",
]
for y, row in enumerate(flame):
    for x, ch in enumerate(row):
        if ch == "#":
            img.putpixel((10 + x, 36 + y), hexc("8b8b8b"))

# little stone ring under the size buttons, purely decorative divider
d.rectangle([110, 16, 110, 78], fill=hexc("a0a0a0"))
d.rectangle([111, 16, 111, 78], fill=HI)

os.makedirs(os.path.join(T, "gui"), exist_ok=True)
img.save(os.path.join(T, "gui", "campfire.png"))
print("fire art done")

"""Textures for clay bricks and the brick oven.

Run: python3 tools/make_oven_art.py   (from the repo root)
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")


def jitter(c, n, rnd):
    d = rnd.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def save(img, rel):
    p = os.path.join(T, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def brick_face(base, fibre, seed, streaks=True):
    """A brick's surface: mottled clay with a few straw fibres pressed in."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(base, 9, rnd))
    if streaks:
        for _ in range(7):
            x, y = rnd.randint(0, 13), rnd.randint(0, 15)
            for i in range(rnd.randint(2, 3)):
                img.putpixel((min(15, x + i), y), jitter(fibre, 8, rnd))
    return img


save(brick_face((112, 116, 122), (176, 160, 104), 1), "block/clay_brick_wet.png")
save(brick_face((196, 160, 118), (222, 200, 140), 2), "block/clay_brick_dry.png")


def wall(face, mortar, seed, soot=0.0):
    """Courses of bricks, offset every other row."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        row = y // 4
        for x in range(16):
            xs = (x + (4 if row % 2 else 0)) % 16
            joint = y % 4 == 3 or xs % 8 == 7
            c = mortar if joint else face
            px = jitter(c, 7 if not joint else 4, rnd)
            if soot and rnd.random() < soot * (1.0 - y / 20):
                px = tuple(int(v * 0.55) for v in px[:3]) + (255,)
            img.putpixel((x, y), px)
    # a few lighter edges on each brick
    for y in range(0, 16, 4):
        for x in range(16):
            xs = (x + (4 if (y // 4) % 2 else 0)) % 16
            if xs % 8 != 7:
                p = img.getpixel((x, y))
                img.putpixel((x, y), tuple(min(255, v + 14) for v in p[:3]) + (255,))
    return img


save(wall((190, 152, 110), (150, 132, 112), 3), "block/oven_wall.png")
save(wall((164, 92, 62), (96, 80, 72), 4, soot=0.35), "block/oven_wall_formed.png")


def brick_icon(top, side, dark):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # a brick seen from above at an angle: top face and front face
    for y in range(5, 9):
        for x in range(2 + (8 - y), 14 + (8 - y) - 3):
            img.putpixel((x, y), top)
    for y in range(9, 12):
        for x in range(2, 13):
            img.putpixel((x, y), side)
    for y in range(5, 9):
        img.putpixel((14 + (8 - y) - 3, y), dark)
    for y in range(9, 12):
        img.putpixel((13, y), dark)
    for x in range(2, 13):
        img.putpixel((x, 12), dark)
    return img


save(brick_icon((150, 154, 160, 255), (110, 114, 120, 255), (70, 72, 78, 255)), "item/wet_clay_brick.png")
save(brick_icon((222, 186, 140, 255), (188, 150, 108, 255), (128, 98, 70, 255)), "item/dry_clay_brick.png")
print("oven art done")

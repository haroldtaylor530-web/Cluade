"""Textures for food spoilage and preserving: jerky, dried fish, dried fruit, rotten food, salt,
a cup of sea water, salt in the cooking pot and the food poisoning icon.

Run: python3 tools/make_food_art.py   (from the repo root)
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")


def save(img, rel):
    p = os.path.join(T, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def blank(n=16):
    return Image.new("RGBA", (n, n), (0, 0, 0, 0))


def outline(img, colour):
    """A dark edge around everything drawn, like vanilla item icons."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3]:
                    img.putpixel((x, y), colour)
                    break
    return img


# ---------------------------------------------------------------- jerky: two dark wrinkled strips
def jerky():
    rnd = random.Random(11)
    img = blank()
    base = (122, 52, 34)
    for strip, (x0, y0) in enumerate(((2, 9), (5, 12))):
        for i in range(9):
            x, y = x0 + i, y0 - i
            for t in range(3):
                px, py = x + (t if strip else 0), y + (0 if strip else t)
                if 0 <= px < 16 and 0 <= py < 16:
                    f = 0.8 + rnd.random() * 0.35
                    if (i + t) % 3 == 0:
                        f *= 0.75  # wrinkles
                    img.putpixel((px, py), shade(base, f))
    # pale streaks of fat
    for x, y in ((4, 9), (7, 6), (9, 8), (11, 6)):
        img.putpixel((x, y), (176, 112, 84, 255))
    return outline(img, (52, 22, 14, 255))


# ---------------------------------------------------------------- dried fish: split and flattened, tail at the top right
def dried_fish():
    import math
    rnd = random.Random(12)
    img = blank()
    body = (184, 164, 120)
    ax, ay, bx, by = 2.5, 13.5, 12.5, 3.5
    lx, ly = bx - ax, by - ay
    ln = math.hypot(lx, ly)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            t = (px * lx + py * ly) / (ln * ln)
            d = abs(px * ly - py * lx) / ln
            if 0.0 <= t <= 1.0 and d <= 0.6 + 2.6 * math.sin(math.pi * min(1.0, t * 1.15)):
                f = 0.88 + rnd.random() * 0.2
                if d < 0.7:
                    f *= 0.78  # backbone
                img.putpixel((x, y), shade(body, f))
    for px, py in ((12, 1), (13, 1), (14, 1), (14, 2), (14, 3), (13, 2)):
        img.putpixel((px, py), shade(body, 0.72))  # tail
    img.putpixel((3, 12), (60, 50, 40, 255))
    return outline(img, (74, 62, 44, 255))


# ---------------------------------------------------------------- dried fruit: a few shrivelled rings
def dried_fruit():
    img = blank()
    rings = (((5, 6), (196, 112, 50)), ((10, 5), (170, 70, 52)), ((7, 11), (206, 140, 64)))
    for (cx, cy), col in rings:
        for y in range(-3, 4):
            for x in range(-3, 4):
                d = x * x + y * y
                if d <= 9:
                    f = 0.75 if d >= 7 else (1.05 if d <= 1 else 0.9)
                    img.putpixel((cx + x, cy + y), shade(col, f))
        img.putpixel((cx, cy), shade(col, 1.25))
    return outline(img, (78, 40, 22, 255))


# ---------------------------------------------------------------- rotten food: a mouldy lump
def rotten_food():
    rnd = random.Random(14)
    img = blank()
    base = (98, 92, 46)
    for y in range(4, 14):
        for x in range(2, 14):
            dx, dy = (x - 7.5) / 6.0, (y - 9.0) / 4.8
            if dx * dx + dy * dy <= 1.0:
                img.putpixel((x, y), shade(base, 0.7 + rnd.random() * 0.45))
    for x, y in ((5, 7), (6, 7), (9, 10), (10, 10), (10, 9), (4, 11), (8, 6)):
        img.putpixel((x, y), (168, 186, 120, 255))  # mould
    for x, y in ((7, 9), (11, 8), (6, 12)):
        img.putpixel((x, y), (54, 48, 26, 255))
    for x, y in ((6, 2), (9, 1), (10, 3)):
        img.putpixel((x, y), (120, 150, 80, 160))  # a whiff
    return outline(img, (40, 36, 18, 255))


# ---------------------------------------------------------------- salt: a little heap of crystals
def salt():
    rnd = random.Random(15)
    img = blank()
    for y in range(6, 14):
        half = (y - 5) * 0.9
        for x in range(16):
            if abs(x - 7.5) <= half:
                v = 205 + rnd.randint(-20, 40)
                img.putpixel((x, y), (min(255, v), min(255, v), min(255, v + 6), 255))
    for x, y in ((7, 7), (5, 10), (10, 9), (8, 12), (3, 12), (12, 12)):
        img.putpixel((x, y), (255, 255, 255, 255))
    for x, y in ((6, 9), (9, 11), (11, 12)):
        img.putpixel((x, y), (168, 172, 182, 255))
    return outline(img, (120, 124, 134, 255))


def salt_water_cup():
    img = Image.open(os.path.join(T, "item", "dirty_water_cup.png")).convert("RGBA")
    swap = {(93, 110, 58, 255): (56, 104, 150, 255), (120, 143, 75, 255): (110, 168, 204, 255)}
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p in swap:
                img.putpixel((x, y), swap[p])
    return img


def pot_salt():
    rnd = random.Random(16)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 222 + rnd.randint(-22, 30)
            img.putpixel((x, y), (min(255, v), min(255, v), min(255, v + 4), 255))
    return img


def poisoning_icon():
    """A green-tinged drumstick with a queasy swirl."""
    img = blank(18)
    meat = (150, 150, 70)
    for y in range(3, 12):
        for x in range(3, 13):
            dx, dy = (x - 7.5) / 4.6, (y - 7.0) / 4.2
            if dx * dx + dy * dy <= 1.0:
                img.putpixel((x, y), shade(meat, 0.85 + 0.25 * ((x + y) % 2)))
    for i in range(5):
        img.putpixel((10 + i, 11 + i), (232, 226, 206, 255))  # bone
        img.putpixel((11 + i, 11 + i), (210, 204, 186, 255))
    img.putpixel((15, 15), (240, 236, 220, 255))
    img.putpixel((16, 14), (240, 236, 220, 255))
    for x, y in ((5, 6), (6, 5), (7, 5), (8, 6), (8, 7), (7, 8), (6, 7)):
        img.putpixel((x, y), (70, 110, 40, 255))  # swirl
    return outline(img, (40, 46, 20, 255))


save(jerky(), "item/jerky.png")
save(dried_fish(), "item/dried_fish.png")
save(dried_fruit(), "item/dried_fruit.png")
save(rotten_food(), "item/rotten_food.png")
save(salt(), "item/salt.png")
save(salt_water_cup(), "item/salt_water_cup.png")
save(pot_salt(), "block/pot_salt.png")
save(poisoning_icon(), "mob_effect/food_poisoning.png")
print("food art done")

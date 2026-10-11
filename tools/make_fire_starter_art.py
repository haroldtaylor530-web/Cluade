"""Textures for the flint striker and the fire starter.

flint_striker: a blob cut from YOUR rock texture, with my flint shard icon on top and a couple of sparks.
fire_starter:  a short length of vanilla stripped oak log, tied with plant fibre.

Run: python3 tools/make_fire_starter_art.py   (from the repo root)
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")
MC = os.environ.get("MC_ASSETS", "/tmp/claude-0/-home-claude/13e7e70a-1578-5504-8d54-6e4003662b91/scratchpad/mc/src/assets/minecraft/textures")


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def outline(img, colour):
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), colour)
    return out


# ---------------------------------------------------------------- flint striker
rock = Image.open(os.path.join(T, "block", "rock.png")).convert("RGBA")
flint = Image.open(os.path.join(T, "item", "flint_shard.png")).convert("RGBA")
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
rock_px = 0
cx, cy, r = 5.5, 10.5, 4.6
for y in range(16):
    for x in range(16):
        if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
            c = rock.getpixel((x, y))
            if y < cy - 2.5:
                c = shade(c, 1.2)
            elif y > cy + 2.5:
                c = shade(c, 0.8)
            else:
                rock_px += 1
            img.putpixel((x, y), c)
small = flint.resize((10, 10), Image.NEAREST)
flint_px = 0
for y in range(10):
    for x in range(10):
        c = small.getpixel((x, y))
        if c[3] > 0:
            img.putpixel((x + 6, y), c)
            flint_px += 1
for (x, y) in ((5, 6), (4, 4), (7, 5)):
    img.putpixel((x, y), (255, 222, 90, 255))
img.putpixel((6, 4), (255, 150, 40, 255))
img = outline(img, (28, 22, 20, 255))
img.save(os.path.join(T, "item", "flint_striker.png"))
print(f"flint_striker: {rock_px} pixels straight from your rock texture, {flint_px} from the flint shard icon")

# ---------------------------------------------------------------- fire starter
side = Image.open(os.path.join(MC, "block", "stripped_oak_log.png")).convert("RGBA")
top = Image.open(os.path.join(MC, "block", "stripped_oak_log_top.png")).convert("RGBA")
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
log_px = 0
for y in range(6, 12):
    for x in range(2, 14):
        c = side.getpixel((x, y - 4))
        if y == 6:
            c = shade(c, 1.15)
        elif y == 11:
            c = shade(c, 0.78)
        img.putpixel((x, y), c)
        log_px += 1
for y in range(6, 12):
    img.putpixel((2, y), top.getpixel((6, y)))  # cut end
    img.putpixel((13, y), shade(top.getpixel((6, y)), 0.85))
img_fibre = [(138, 122, 58, 255), (122, 168, 62, 255), (76, 118, 40, 255)]
for x in (5, 6, 9, 10):
    for y in range(5, 13):
        img.putpixel((x, y), img_fibre[0] if x in (5, 9) else shade(img_fibre[0], 0.8))
for (x, y, c) in ((4, 4, 1), (6, 3, 2), (8, 4, 1), (10, 3, 2), (11, 4, 1), (3, 12, 2), (7, 13, 1), (12, 13, 2)):
    img.putpixel((x, y), img_fibre[c])
    img.putpixel((x, y + (1 if y < 6 else -1)), img_fibre[c])
img = outline(img, (44, 30, 18, 255))
img.save(os.path.join(T, "item", "fire_starter.png"))
print(f"fire_starter: {log_px} pixels from the vanilla stripped oak log texture, the rest is my plant fibre colours")

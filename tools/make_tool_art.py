"""Whetstone texture, cut out of your rock texture.

Run: python3 tools/make_tool_art.py   (from the repo root)
Prints how many of the pixels are straight from the rock texture.
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")
rock = Image.open(os.path.join(T, "block", "rock.png")).convert("RGBA")


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def dist_to_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
inside = set()
for y in range(16):
    for x in range(16):
        if dist_to_segment(x + 0.5, y + 0.5, 3.0, 12.0, 12.5, 4.0) <= 2.6:
            inside.add((x, y))
direct = 0
for (x, y) in inside:
    c = rock.getpixel((x, y))
    up = (x, y - 1) not in inside or (x + 1, y - 1) not in inside and False
    edge_light = (x, y - 1) not in inside
    edge_dark = (x, y + 1) not in inside or (x + 1, y) not in inside
    if edge_light:
        c = shade(c, 1.25)
    elif edge_dark:
        c = shade(c, 0.8)
    else:
        direct += 1
    img.putpixel((x, y), c)
dark = shade(min((rock.getpixel((x, y)) for x in range(16) for y in range(16)), key=sum), 0.35)
for y in range(16):
    for x in range(16):
        if (x, y) in inside:
            continue
        if any((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            img.putpixel((x, y), dark)
os.makedirs(os.path.join(T, "item"), exist_ok=True)
img.save(os.path.join(T, "item", "whetstone.png"))
print(f"whetstone: {direct} of {len(inside)} body pixels straight from the rock texture")

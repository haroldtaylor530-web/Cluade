"""Hand drill icon: a stick cut from YOUR stick texture, wound with vanilla string.

Run: python3 tools/make_hand_drill_art.py   (from the repo root)
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")
MC = os.environ.get("MC_ASSETS", "/tmp/claude-0/-home-claude/13e7e70a-1578-5504-8d54-6e4003662b91/scratchpad/mc/src/assets/minecraft/textures")
stick = Image.open(os.path.join(T, "block", "stick_on_ground.png")).convert("RGBA")
string = Image.open(os.path.join(MC, "item", "string.png")).convert("RGBA")
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
# a diagonal stick 2 pixels thick; each pixel takes the next pixel of the wood strip along its length
body = []
for i in range(14):
    x, y = 1 + i, 14 - i
    body += [(x, y), (x + 1, y)] if i % 2 == 0 else [(x, y), (x, y - 1)]
body = sorted(set(body))
from_stick = 0
for n, (x, y) in enumerate(body):
    c = stick.getpixel((n % 16, (n // 16 + n) % 2))
    img.putpixel((x, y), c)
    from_stick += 1
# string wound round the middle, a loose end hanging off
white = (247, 247, 247, 255)
grey = (219, 219, 219, 255)
for i, (x, y) in enumerate(((6, 9), (7, 8), (8, 7), (9, 6), (6, 8), (8, 8), (7, 7), (9, 7))):
    img.putpixel((x, y), white if i % 2 == 0 else grey)
for (x, y) in ((5, 10), (4, 11), (4, 12)):
    img.putpixel((x, y), grey)
# dark outline
out = img.copy()
for y in range(16):
    for x in range(16):
        if img.getpixel((x, y))[3] == 0 and any(
                0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out.putpixel((x, y), (40, 28, 12, 255))
out.save(os.path.join(T, "item", "hand_drill.png"))
print(f"hand_drill: {from_stick} stick pixels taken from your stick texture, a few string pixels from vanilla's string texture")

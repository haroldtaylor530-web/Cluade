"""The mod's logo: your rock texture as the background, the mod's item icons in a ring, a big spear in the middle.

Run: python3 tools/make_logo.py   (from the repo root). Writes logo.png (512x512).
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival", "textures")
S = 512


def icon(name):
    return Image.open(os.path.join(T, "item", name + ".png")).convert("RGBA")


def scaled(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


# background: your rock texture tiled, darkened, with a warm glow in the middle
rock = Image.open(os.path.join(T, "block", "rock.png")).convert("RGB")
bg = Image.new("RGB", (S, S))
for y in range(0, S, 32):
    for x in range(0, S, 32):
        bg.paste(rock.resize((32, 32), Image.NEAREST), (x, y))
bg = Image.eval(bg, lambda v: int(v * 0.42))
glow = Image.new("RGB", (S, S), (255, 150, 60))
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).ellipse((96, 40, S - 96, S - 200), fill=120)
mask = mask.filter(ImageFilter.GaussianBlur(70))
bg = Image.composite(glow, bg, mask)
# vignette
vig = Image.new("L", (S, S), 0)
ImageDraw.Draw(vig).ellipse((-90, -90, S + 90, S + 90), fill=255)
vig = vig.filter(ImageFilter.GaussianBlur(70))
bg = Image.composite(bg, Image.new("RGB", (S, S), (8, 8, 12)), vig)
img = bg.convert("RGBA")

# ring of icons
ring = ["flint_hatchet", "fire_starter", "waterskin_full", "hand_drill", "jerky", "whetstone",
        "flint_striker", "clay_cup", "fur_hat", "wet_clay_brick", "flint_pickaxe", "bandage"]
cx, cy, r = S // 2, 205, 150
for i, name in enumerate(ring):
    a = -math.pi / 2 + i * 2 * math.pi / len(ring)
    ic = scaled(icon(name), 3)
    shadow = Image.new("RGBA", ic.size, (0, 0, 0, 0))
    shadow.putalpha(ic.getchannel("A").point(lambda v: 150 if v else 0))
    x, y = int(cx + r * math.cos(a) - ic.width / 2), int(cy + r * math.sin(a) - ic.height / 2)
    img.alpha_composite(shadow, (x + 4, y + 4))
    img.alpha_composite(ic, (x, y))

# the big spear in the middle
spear = scaled(icon("flint_spear_icon"), 10)
sh = Image.new("RGBA", spear.size, (0, 0, 0, 0))
sh.putalpha(spear.getchannel("A").point(lambda v: 170 if v else 0))
sx, sy = cx - spear.width // 2, cy - spear.height // 2
img.alpha_composite(sh, (sx + 8, sy + 8))
img.alpha_composite(spear, (sx, sy))

# title
d = ImageDraw.Draw(img)
font = ImageFont.truetype("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf", 46)
small = ImageFont.truetype("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf", 26)
for text, f, y, col in (("FLINT", font, 376, (255, 226, 170)), ("& FIRE", font, 425, (255, 226, 170))):
    w = d.textlength(text, font=f)
    for dx, dy in ((-3, 0), (3, 0), (0, -3), (0, 3), (3, 3), (-3, -3), (3, -3), (-3, 3)):
        d.text(((S - w) / 2 + dx, y + dy), text, font=f, fill=(20, 10, 4, 255))
    d.text(((S - w) / 2, y), text, font=f, fill=col)
img.convert("RGB").save(os.path.join(ROOT, "logo.png"))
print("logo.png written")

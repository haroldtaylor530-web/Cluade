"""Converts the Blockbench models in tools/models/*.bbmodel into Minecraft JSON models and textures.

Run: python3 tools/convert_bbmodels.py   (from the repo root)

- Flint_Spear.bbmodel     -> item/flint_spear (+ iron and diamond with recoloured tips)
- Rock.bbmodel            -> block/rock
- Stick_On_Ground.bbmodel -> block/stick_on_ground
- Cooking_pot.bbmodel     -> block/cooking_pot (+ water levels)
"""
import base64
import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "models")
A = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival")
NS = "realisticsurvival"
ALLOWED_ANGLES = (-45, -22.5, 0, 22.5, 45)


def load(name):
    with open(os.path.join(SRC, name)) as f:
        return json.load(f)


def texture_image(tex):
    return Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")


def save_png(img, rel):
    p = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def save_json(obj, rel):
    p = os.path.join(A, "models", rel + ".json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def bake_rotation(el):
    """Blockbench allows any rotation; Minecraft only 22.5 steps on one axis.
    A 180 degree turn is baked into the box; other angles snap to the nearest allowed one."""
    rot = el.get("rotation") or [0, 0, 0]
    frm, to, origin = list(el["from"]), list(el["to"]), el.get("origin", [8, 8, 8])
    out_rot = None
    for axis, angle in enumerate(rot):
        if not angle:
            continue
        if abs(abs(angle) - 180) < 1e-6:
            # turning 180 about one axis mirrors the other two axes through the origin
            for a in range(3):
                if a != axis:
                    lo, hi = 2 * origin[a] - to[a], 2 * origin[a] - frm[a]
                    frm[a], to[a] = lo, hi
        else:
            snapped = min(ALLOWED_ANGLES, key=lambda x: abs(x - angle))
            out_rot = {"angle": snapped, "axis": "xyz"[axis], "origin": origin}
    return frm, to, out_rot


def convert(bb, texture_names, extra_elements=(), parent=None, display=None):
    elements = []
    for el in bb["elements"]:
        if not el.get("export", True):
            continue
        frm, to, rot = bake_rotation(el)
        faces = {}
        for d, fc in el["faces"].items():
            t = fc.get("texture")
            if t is None:
                continue
            faces[d] = {"uv": fc["uv"], "texture": f"#{t}"}
        if not faces:
            continue
        e = {"from": frm, "to": to, "faces": faces}
        if rot:
            e["rotation"] = rot
        elements.append(e)
    elements.extend(extra_elements)
    textures = {str(i): n for i, n in enumerate(texture_names)}
    textures["particle"] = texture_names[0]
    model = {}
    if parent:
        model["parent"] = parent
    model["textures"] = textures
    model["elements"] = elements
    if display:
        model["display"] = display
    return model


def recolour(img, dark, light):
    """Map a dark/light two-tone texture onto new colours by brightness."""
    px = [p for p in img.getdata() if p[3]]
    lums = [sum(p[:3]) for p in px]
    lo, hi = min(lums), max(lums)
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            p = img.getpixel((x, y))
            if not p[3]:
                continue
            t = 0.0 if hi == lo else (sum(p[:3]) - lo) / (hi - lo)
            out.putpixel((x, y), tuple(int(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + (255,))
    return out


# ------------------------------------------------------------------ spear
spear = load("Flint_Spear.bbmodel")
stick_tex = texture_image(spear["textures"][0])
tip_tex = texture_image(spear["textures"][1])
save_png(stick_tex, "item/spear_shaft")
TIPS = {
    "flint": None,
    "iron": ((150, 150, 156), (226, 226, 232)),
    "diamond": ((24, 132, 122), (110, 240, 222)),
}
SPEAR_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 60], "translation": [0, 4, 1], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -60], "translation": [0, 4, 1], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 30], "translation": [1.5, 2, 1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 90, -30], "translation": [1.5, 2, 1], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 0, 45], "translation": [0, 10, 0]},
}
for mat, cols in TIPS.items():
    tip = tip_tex if cols is None else recolour(tip_tex, *cols)
    save_png(tip, f"item/{mat}_spear_tip")
    save_json(convert(spear, [f"{NS}:item/spear_shaft", f"{NS}:item/{mat}_spear_tip"], display=SPEAR_DISPLAY), f"item/{mat}_spear")

# inventory icons in the same colours as the model
for mat in TIPS:
    p = os.path.join(A, "textures", "item", f"{mat}_spear_icon.png")
    icon = Image.open(p).convert("RGBA")
    tip = Image.open(os.path.join(A, "textures", "item", f"{mat}_spear_tip.png")).convert("RGBA")
    tip_cols = sorted({tip.getpixel((x, y))[:3] for x in range(16) for y in range(16) if tip.getpixel((x, y))[3]}, key=sum)
    wood = sorted({stick_tex.getpixel((x, y))[:3] for x in range(16) for y in range(16) if stick_tex.getpixel((x, y))[3]}, key=sum)
    out = icon.copy()
    for y in range(16):
        for x in range(16):
            px = icon.getpixel((x, y))
            if not px[3]:
                continue
            r, g, b = px[:3]
            is_wood = r > g > b and r - b > 35 and not (r > 170 and g > 150)
            if is_wood:
                c = wood[min(len(wood) - 1, int(sum(px[:3]) / 765 * len(wood) * 1.6))]
                out.putpixel((x, y), c + (255,))
            elif not (r > 170 and g > 150 and b < 140):  # keep the binding colour
                c = tip_cols[min(len(tip_cols) - 1, int(sum(px[:3]) / 765 * len(tip_cols) * 1.4))]
                out.putpixel((x, y), c + (255,))
    out.save(p)

# ------------------------------------------------------------------ rock
rock = load("Rock.bbmodel")
save_png(texture_image(rock["textures"][0]), "block/rock")
save_json(convert(rock, [f"{NS}:block/rock"], parent="minecraft:block/block"), "block/rock")

# ------------------------------------------------------------------ stick on the ground
stick = load("Stick_On_Ground.bbmodel")
save_png(texture_image(stick["textures"][0]), "block/stick_on_ground")
save_json(convert(stick, [f"{NS}:block/stick_on_ground"], parent="minecraft:block/block"), "block/stick_on_ground")

# ------------------------------------------------------------------ cooking pot
pot = load("Cooking_pot.bbmodel")
save_png(texture_image(pot["textures"][0]), "block/cooking_pot")
save_png(texture_image(pot["textures"][1]), "block/cooking_pot_handles")


def water_texture(base, seed):
    import random
    random.seed(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = random.randint(-12, 12)
            img.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in base) + (230,))
    return img


save_png(water_texture((88, 104, 58), 1), "block/pot_water_dirty")
save_png(water_texture((62, 112, 222), 2), "block/pot_water_clean")
POT_TEX = [f"{NS}:block/cooking_pot", f"{NS}:block/cooking_pot_handles"]
save_json(convert(pot, POT_TEX, parent="minecraft:block/block"), "block/cooking_pot")
for level in range(1, 5):
    y = 2 + level * 2.4
    for kind in ("dirty", "clean"):
        surface = {"from": [2, y, 2], "to": [14, y, 14],
                   "faces": {"up": {"uv": [2, 2, 14, 14], "texture": "#water"}}}
        m = convert(pot, POT_TEX, extra_elements=[surface], parent="minecraft:block/block")
        m["textures"]["water"] = f"{NS}:block/pot_water_{kind}"
        save_json(m, f"block/cooking_pot_{level}_{kind}")


def lowered(model, dy=-9):
    import copy
    m = copy.deepcopy(model)
    for e in m["elements"]:
        e["from"][1] += dy
        e["to"][1] += dy
        if "rotation" in e:
            e["rotation"]["origin"] = [e["rotation"]["origin"][0], e["rotation"]["origin"][1] + dy, e["rotation"]["origin"][2]]
    return m


import glob
for path in glob.glob(os.path.join(A, "models", "block", "cooking_pot*.json")):
    if path.endswith("_lowered.json"):
        continue
    with open(path) as f:
        m = json.load(f)
    save_json(lowered(m), "block/" + os.path.basename(path)[:-5] + "_lowered")
print("converted")

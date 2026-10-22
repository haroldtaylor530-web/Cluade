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


PICTURES = {
    "skull_side": SKULL_SIDE, "skull_front": SKULL_FRONT, "skull_top": SKULL_TOP,
    "vertebra_end": VERTEBRA_END, "spine_side": SPINE_SIDE, "spine_top": SPINE_TOP,
    "ribs_side": RIBS_SIDE, "ribs_front": RIBS_FRONT, "ribs_top": RIBS_TOP,
    "tail_side": TAIL_SIDE, "tail_end": TAIL_END, "tail_top": TAIL_TOP,
    "limb_side": LIMB_SIDE, "limb_end": LIMB_END, "foot_side": FOOT_SIDE,
    "ammonite": ammonite(), "trilobite": TRILOBITE, "fish": FISH, "fern": fern(),
}

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
PIECE_NAMES = {
    "skull": "Skull", "spine": "Spine", "ribs": "Ribs", "tail": "Tail", "limb": "Leg Bone", "foot": "Foot",
    "ammonite": "Ammonite", "trilobite": "Trilobite", "fish": "Fish", "fern": "Fern",
}


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def hash01(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def render(pic, host, seed):
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


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    tex_dir = os.path.join(ASSETS, "textures", "block", "fossil")
    os.makedirs(tex_dir, exist_ok=True)
    for host in HOSTS:
        for i, (name, pic) in enumerate(PICTURES.items()):
            render(pic, host, i * 31 + len(host)).save(os.path.join(tex_dir, f"{host}_{name}.png"))

    ids = []
    lang = {}
    for host, h in HOSTS.items():
        side, top, bottom = h["tex"]
        for piece, spec in PIECES.items():
            bid = f"{host}_{piece}_fossil"
            ids.append(bid)
            lang[f"block.{MODID}.{bid}"] = f"{h['name']} {PIECE_NAMES[piece]} Fossil"
            textures = {"particle": side, "side": side, "top": top, "bottom": bottom}
            base_faces = {}
            over_faces = {}
            for d in ("north", "south", "east", "west", "up", "down"):
                base_faces[d] = {"texture": "#top" if d == "up" else "#bottom" if d == "down" else "#side", "cullface": d}
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

    write_json(os.path.join(DATA, "minecraft", "tags", "blocks", "mineable", "pickaxe.json"),
               {"replace": False, "values": [f"{MODID}:{b}" for b in ids]})

    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path) as f:
        existing = json.load(f)
    existing = {k: v for k, v in existing.items() if not k.startswith(f"block.{MODID}.")}
    existing.update(lang)
    write_json(lang_path, existing)

    # a contact sheet to look at
    rock = {"stone": (0x7D, 0x7D, 0x7D), "deepslate": (0x4A, 0x4A, 0x4F), "sandstone": (0xD8, 0xCB, 0x9B)}
    sheet = Image.new("RGBA", (len(PICTURES) * 18, len(HOSTS) * 18), (40, 40, 40, 255))
    for j, host in enumerate(HOSTS):
        for i, name in enumerate(PICTURES):
            tile = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    tile.putpixel((x, y), shade(rock[host], 0.85 + 0.3 * hash01(x, y, 5)) + (255,))
            tile.alpha_composite(Image.open(os.path.join(tex_dir, f"{host}_{name}.png")))
            sheet.paste(tile, (i * 18 + 1, j * 18 + 1))
    out = os.environ.get("FOSSIL_SHEET")
    if out:
        sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save(out)
    print(len(ids), "fossil blocks")


if __name__ == "__main__":
    main()

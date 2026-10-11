"""Hold and charge poses for the spears, matched to vanilla's spear pose.

Your Flint_Spear model has its tip along +x. Vanilla's spear (item/spear_in_hand) is a flat
sprite whose tip points up-left, with the display transform [-20, 90, -35] in first person and
[5, 270, -40] in third. Display rotations apply X, then Y, then Z, so the angles that send your +x
tip to the direction vanilla's tip ends up pointing (forward and up, about 43 degrees) are
[0, -45, 75] in first person and [0, 45, 55] in third.

This also centres the model on the hand (shifts it 3, -2.5, -0.5 units), so the thrown-spear
renderer no longer has to, and writes the pull-back models that the item definition swaps in
while you charge a throw.

Run after convert_bbmodels.py: python3 tools/fix_spear_models.py   (from the repo root)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "realisticsurvival")
NS = "realisticsurvival"
SHIFT = (3.0, -2.5, -0.5)


def hold(pull):
    """Display transforms; pull 0..3 draws the spear back towards the shoulder."""
    z = 0.13 - 2.2 * pull
    y = 2.0 + 0.6 * pull
    rz = 75 + 5 * pull
    return {
        "firstperson_righthand": {"rotation": [0, -45, rz], "translation": [3.13, y, z], "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [0, 45, -rz], "translation": [3.13, y, z], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_righthand": {"rotation": [0, 45, 55], "translation": [0, 2, 2], "scale": [1.05, 1.05, 1.05]},
        "thirdperson_lefthand": {"rotation": [0, -45, -55], "translation": [0, 2, 2], "scale": [1.05, 1.05, 1.05]},
        "head": {"rotation": [0, 0, 45], "translation": [0, 10, 0]},
    }


def model(name):
    return {"type": "minecraft:model", "model": f"{NS}:item/{name}"}


for mat in ("flint", "iron", "diamond"):
    path = os.path.join(A, "models", "item", f"{mat}_spear.json")
    with open(path) as f:
        m = json.load(f)
    if min(e["from"][0] for e in m["elements"]) < -12:  # not centred yet
        for e in m["elements"]:
            e["from"] = [round(v + d, 4) for v, d in zip(e["from"], SHIFT)]
            e["to"] = [round(v + d, 4) for v, d in zip(e["to"], SHIFT)]
    m["display"] = hold(0)
    with open(path, "w") as f:
        json.dump(m, f, indent=2)
        f.write("\n")
    for i in (1, 2, 3):
        p = dict(m)
        p["display"] = hold(i)
        with open(os.path.join(A, "models", "item", f"{mat}_spear_pull_{i}.json"), "w") as f:
            json.dump(p, f, indent=2)
            f.write("\n")
    item = {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": model(f"{mat}_spear_icon")}],
        "fallback": {
            "type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": model(f"{mat}_spear"),
            "on_true": {
                "type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                "entries": [{"threshold": 0.3, "model": model(f"{mat}_spear_pull_2")},
                            {"threshold": 0.65, "model": model(f"{mat}_spear_pull_3")}],
                "fallback": model(f"{mat}_spear_pull_1")}}}}
    with open(os.path.join(A, "items", f"{mat}_spear.json"), "w") as f:
        json.dump(item, f, indent=2)
        f.write("\n")
print("spear models done")

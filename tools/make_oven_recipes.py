"""Moves stone/metal-work recipes from the crafting table to the brick oven.

Each vanilla recipe listed here is overridden (same id, same pattern) with the oven recipe
type, so it can only be made in a working brick oven. Flint and steel is left alone.

Run: python3 tools/make_oven_recipes.py <vanilla data/minecraft/recipe folder> [<NeoForge's generated data/minecraft/recipe folder>]
NeoForge ships its own copies of many vanilla recipes (using c: tags); those are preferred.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "minecraft", "recipe")
SRC = sys.argv[1]
NEO = sys.argv[2] if len(sys.argv) > 2 else None

STATIONS = ["furnace", "smoker", "blast_furnace", "stonecutter", "anvil", "grindstone", "smithing_table"]
PIECES = ["pickaxe", "axe", "shovel", "hoe", "sword", "spear", "helmet", "chestplate", "leggings", "boots"]
GEAR = [f"{m}_{p}" for m in ("iron", "golden", "copper") for p in PIECES] + ["shield"]
GOODS = ["bucket", "shears", "rail", "powered_rail", "detector_rail", "activator_rail", "cauldron", "hopper",
         "iron_chain", "copper_chain", "lantern", "soul_lantern", "copper_lantern", "iron_door", "iron_trapdoor",
         "iron_bars", "copper_bars", "copper_door", "copper_trapdoor", "minecart", "compass", "clock", "lightning_rod"]
TYPES = {"minecraft:crafting_shaped": "realisticsurvival:oven_shaped",
         "minecraft:crafting_shapeless": "realisticsurvival:oven_shapeless"}

os.makedirs(OUT, exist_ok=True)
moved = 0
for name in STATIONS + GEAR + GOODS:
    path = os.path.join(SRC, name + ".json")
    if NEO and os.path.exists(os.path.join(NEO, name + ".json")):
        path = os.path.join(NEO, name + ".json")
    if not os.path.exists(path):
        print("no vanilla recipe:", name)
        continue
    with open(path) as f:
        recipe = json.load(f)
    if recipe["type"] not in TYPES:
        print("skipped (type", recipe["type"] + "):", name)
        continue
    recipe["type"] = TYPES[recipe["type"]]
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(recipe, f, indent=2)
        f.write("\n")
    moved += 1
with open(os.path.join(ROOT, "tools", "oven_recipes.txt"), "w") as f:
    f.write("\n".join(STATIONS + GEAR + GOODS) + "\n")
print("moved", moved, "recipes to the oven")

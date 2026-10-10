"""Writes models, item definitions, recipes, tags, damage types and lang for Realistic Survival.

Run: python3 make_data.py <repo>/src/main/resources
"""
import json
import os
import sys

RES = sys.argv[1]
NS = "realisticsurvival"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data")


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def ns(x):
    return x if ":" in x else f"{NS}:{x}"


# ------------------------------------------------------------------ item models
FLAT = ["flint_shard", "plant_fibre", "fibre_string", "unfired_clay_cup", "clay_cup", "dirty_water_cup",
        "clean_water_cup", "bandage", "splint", "fur_pelt", "fur_hat", "fur_coat", "fur_trousers", "fur_boots",
        "waterskin", "waterskin_full", "flint_spear_icon", "iron_spear_icon", "diamond_spear_icon"]
TOOLS = ["flint_knife", "flint_hatchet", "flint_pickaxe"]

for name in FLAT:
    w(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
for name in TOOLS:
    w(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})


# 3D spear models come from tools/convert_bbmodels.py (your Blockbench model)


# ------------------------------------------------------------------ client item definitions (assets/<ns>/items)
def model(m):
    return {"type": "minecraft:model", "model": ns(m)}


for name in FLAT + TOOLS:
    if name.endswith("_icon") or name == "waterskin_full":
        continue
    if name == "waterskin":
        w(f"{A}/items/waterskin.json", {"model": {
            "type": "minecraft:select", "property": "minecraft:component", "component": f"{NS}:water_amount",
            "cases": [{"when": 0, "model": model("item/waterskin")}],
            "fallback": model("item/waterskin_full")}})
        continue
    w(f"{A}/items/{name}.json", {"model": model(f"item/{name}")})

# spears: 2D icon in inventories/frames/on the ground, the 3D model in hand and when thrown
for mat in ("flint", "iron", "diamond"):
    w(f"{A}/items/{mat}_spear.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": model(f"item/{mat}_spear_icon")}],
        "fallback": model(f"item/{mat}_spear")}})

# fur armour on the body
w(f"{A}/equipment/fur.json", {"layers": {
    "humanoid": [{"texture": f"{NS}:fur"}],
    "humanoid_leggings": [{"texture": f"{NS}:fur"}],
    "humanoid_baby": [{"texture": f"{NS}:fur"}]}})

# ------------------------------------------------------------------ recipes
R = f"{D}/{NS}/recipe"


def result(item, count=1):
    return {"id": ns(item), "count": count}


def shaped(name, pattern, key, out, count=1, cat="misc"):
    w(f"{R}/{name}.json", {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
                           "key": {k: v for k, v in key.items()}, "result": result(out, count)})


def shapeless(name, ings, out, count=1, cat="misc"):
    w(f"{R}/{name}.json", {"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": ings,
                           "result": result(out, count)})


def cook(name, kind, ing, out, time, xp=0.1):
    w(f"{R}/{name}.json", {"type": f"minecraft:{kind}", "category": "misc", "ingredient": ns(ing),
                           "result": result(out), "cookingtime": time, "experience": xp})


BIND = f"#{NS}:binding"
shapeless("fibre_string", [ns("plant_fibre")] * 3, "fibre_string")
shapeless("flint_knife", [ns("flint_shard"), "minecraft:stick", BIND], "flint_knife", cat="equipment")
shaped("flint_hatchet", ["FS", " /"], {"F": "minecraft:flint", "S": BIND, "/": "minecraft:stick"}, "flint_hatchet", cat="equipment")
shaped("flint_pickaxe", ["FSF", " / ", " / "], {"F": "minecraft:flint", "S": BIND, "/": "minecraft:stick"}, "flint_pickaxe", cat="equipment")
for mat, tip in (("flint", ns("flint_shard")), ("iron", "minecraft:iron_ingot"), ("diamond", "minecraft:diamond")):
    shaped(f"{mat}_spear", [" ST", " / ", "/  "], {"T": tip, "S": BIND, "/": "minecraft:stick"}, f"{mat}_spear", cat="equipment")
shaped("unfired_clay_cup", ["C C", " C "], {"C": "minecraft:clay_ball"}, "unfired_clay_cup")
cook("clay_cup", "smelting", "unfired_clay_cup", "clay_cup", 200)
cook("boil_water_furnace", "smelting", "dirty_water_cup", "clean_water_cup", 200)
cook("boil_water_smoker", "smoking", "dirty_water_cup", "clean_water_cup", 100)
cook("boil_water_campfire", "campfire_cooking", "dirty_water_cup", "clean_water_cup", 400, 0.0)
shaped("waterskin", [" S ", "L L", " L "], {"S": BIND, "L": "minecraft:leather"}, "waterskin", cat="equipment")
shapeless("bandage_from_wool", ["#minecraft:wool", BIND], "bandage", 2)
shapeless("bandage_from_paper", ["minecraft:paper", "minecraft:paper", BIND], "bandage")
shaped("splint", ["/S/"], {"/": "minecraft:stick", "S": BIND}, "splint")
P = {"P": ns("fur_pelt")}
shaped("fur_hat", ["PPP", "P P"], P, "fur_hat", cat="equipment")
shaped("fur_coat", ["P P", "PPP", "PPP"], P, "fur_coat", cat="equipment")
shaped("fur_trousers", ["PPP", "P P", "P P"], P, "fur_trousers", cat="equipment")
shaped("fur_boots", ["P P", "P P"], P, "fur_boots", cat="equipment")

# Logs give 2 planks by hand instead of 4 (overrides vanilla recipes).
WOODS = {"oak": "#minecraft:oak_logs", "spruce": "#minecraft:spruce_logs", "birch": "#minecraft:birch_logs",
         "jungle": "#minecraft:jungle_logs", "acacia": "#minecraft:acacia_logs", "dark_oak": "#minecraft:dark_oak_logs",
         "mangrove": "#minecraft:mangrove_logs", "cherry": "#minecraft:cherry_logs", "pale_oak": "#minecraft:pale_oak_logs",
         "crimson": "#minecraft:crimson_stems", "warped": "#minecraft:warped_stems"}
for wood, tag in WOODS.items():
    w(f"{D}/minecraft/recipe/{wood}_planks.json", {
        "type": "minecraft:crafting_shapeless", "category": "building", "group": "planks",
        "ingredients": [tag], "result": {"id": f"minecraft:{wood}_planks", "count": 2}})

# ------------------------------------------------------------------ tags
def tag(path, values):
    w(path, {"replace": False, "values": values})


tag(f"{D}/{NS}/tags/item/binding.json", [ns("fibre_string"), "minecraft:string"])
tag(f"{D}/{NS}/tags/item/flint_repair.json", ["minecraft:flint", ns("flint_shard")])
tag(f"{D}/{NS}/tags/item/fur_repair.json", [ns("fur_pelt")])
FUR_ITEMS = [ns(x) for x in ("fur_hat", "fur_coat", "fur_trousers", "fur_boots")]
tag(f"{D}/{NS}/tags/item/warm_clothing.json", FUR_ITEMS)
tag(f"{D}/minecraft/tags/item/freeze_immune_wearables.json", FUR_ITEMS)
tag(f"{D}/minecraft/tags/item/axes.json", [ns("flint_hatchet")])
tag(f"{D}/minecraft/tags/item/pickaxes.json", [ns("flint_pickaxe")])
tag(f"{D}/minecraft/tags/item/head_armor.json", [ns("fur_hat")])
tag(f"{D}/minecraft/tags/item/chest_armor.json", [ns("fur_coat")])
tag(f"{D}/minecraft/tags/item/leg_armor.json", [ns("fur_trousers")])
tag(f"{D}/minecraft/tags/item/foot_armor.json", [ns("fur_boots")])
tag(f"{D}/minecraft/tags/item/enchantable/durability.json",
    [ns(x) for x in ("flint_knife", "flint_hatchet", "flint_pickaxe", "flint_spear", "iron_spear", "diamond_spear")] + FUR_ITEMS)

DAMAGE = {"dehydration": "%1$s died of thirst", "heatstroke": "%1$s died of heatstroke",
          "hypothermia": "%1$s froze to death", "bleeding": "%1$s bled out"}
for name in DAMAGE:
    w(f"{D}/{NS}/damage_type/{name}.json", {"message_id": f"{NS}.{name}", "exhaustion": 0.0, "scaling": "never"})
tag(f"{D}/minecraft/tags/damage_type/bypasses_armor.json", [ns(x) for x in DAMAGE])
tag(f"{D}/minecraft/tags/damage_type/no_knockback.json", [ns(x) for x in DAMAGE])

# ------------------------------------------------------------------ language
lang = {
    "itemGroup.realisticsurvival": "Realistic Survival",
    f"item.{NS}.flint_shard": "Flint Shard",
    f"item.{NS}.plant_fibre": "Plant Fibre",
    f"item.{NS}.fibre_string": "Fibre String",
    f"item.{NS}.flint_knife": "Flint Knife",
    f"item.{NS}.flint_hatchet": "Flint Hatchet",
    f"item.{NS}.flint_pickaxe": "Flint Pickaxe",
    f"item.{NS}.flint_spear": "Flint Spear",
    f"item.{NS}.iron_spear": "Iron Spear",
    f"item.{NS}.diamond_spear": "Diamond Spear",
    f"item.{NS}.unfired_clay_cup": "Unfired Clay Cup",
    f"item.{NS}.clay_cup": "Clay Cup",
    f"item.{NS}.dirty_water_cup": "Cup of Dirty Water",
    f"item.{NS}.clean_water_cup": "Cup of Boiled Water",
    f"item.{NS}.waterskin": "Waterskin",
    f"item.{NS}.waterskin.empty": "Waterskin (empty)",
    f"item.{NS}.waterskin.clean": "Waterskin (%s/%s, clean)",
    f"item.{NS}.waterskin.dirty": "Waterskin (%s/%s, dirty)",
    f"item.{NS}.bandage": "Bandage",
    f"item.{NS}.splint": "Splint",
    f"item.{NS}.fur_pelt": "Fur Pelt",
    f"item.{NS}.fur_hat": "Fur Hat",
    f"item.{NS}.fur_coat": "Fur Coat",
    f"item.{NS}.fur_trousers": "Fur Trousers",
    f"item.{NS}.fur_boots": "Fur Boots",
    f"entity.{NS}.thrown_spear": "Spear",
    f"effect.{NS}.bleeding": "Bleeding",
    f"effect.{NS}.dysentery": "Dysentery",
    f"effect.{NS}.broken_leg": "Broken Leg",
    f"effect.{NS}.splinted": "Splinted Leg",
    f"effect.{NS}.hypothermia": "Hypothermia",
    f"effect.{NS}.heatstroke": "Heatstroke",
    f"message.{NS}.thirsty": "You're very thirsty. Find water.",
    f"message.{NS}.sick": "That water made you sick. Boil it next time.",
    f"message.{NS}.cold": "You're freezing. Get to a fire or put on warm clothes.",
    f"message.{NS}.hot": "You're overheating. Find shade and drink.",
    f"message.{NS}.broken_leg": "You broke your leg! Craft a splint.",
    f"message.{NS}.bleeding": "You're bleeding! Use a bandage.",
    f"message.{NS}.need_axe": "You can't get logs by hand. Make a flint hatchet.",
    f"message.{NS}.sore_hands": "Ouch! Punching that hurts.",
    f"{NS}.configuration.title": "Realistic Survival",
    f"{NS}.configuration.thirst": "Thirst",
    f"{NS}.configuration.thirstRate": "Thirst rate",
    f"{NS}.configuration.temperature": "Body temperature",
    f"{NS}.configuration.injuries": "Injuries",
    f"{NS}.configuration.harderEarlyGame": "Harder early game",
    f"{NS}.configuration.workTiresYou": "Hard work makes you hungry and thirsty",
}
for name, msg in DAMAGE.items():
    lang[f"death.attack.{NS}.{name}"] = msg
    lang[f"death.attack.{NS}.{name}.player"] = msg
w(f"{A}/lang/en_us.json", lang)
print("data done")

# ------------------------------------------------------------------ blocks: rocks, sticks, cooking pot
def blockstate(name, obj):
    w(f"{A}/blockstates/{name}.json", obj)


for name in ("rock", "stick_on_ground"):
    blockstate(name, {"variants": {"": [{"model": f"{NS}:block/{name}", "y": r} for r in (0, 90, 180, 270)]}})

pot_variants = {"water=0": {"model": f"{NS}:block/cooking_pot"}}
for level in range(1, 5):
    pot_variants[f"boiled=false,water={level}"] = {"model": f"{NS}:block/cooking_pot_{level}_dirty"}
    pot_variants[f"boiled=true,water={level}"] = {"model": f"{NS}:block/cooking_pot_{level}_clean"}
blockstate("cooking_pot", {"variants": pot_variants})

w(f"{A}/items/rock.json", {"model": model("block/rock")})
w(f"{A}/items/cooking_pot.json", {"model": model("block/cooking_pot")})


def loot(name, item):
    w(f"{D}/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": item}],
        "condition": {"type": "minecraft:survives_explosion"}}]})


loot("rock", ns("rock"))
loot("stick_on_ground", "minecraft:stick")
loot("cooking_pot", ns("cooking_pot"))

shapeless("cobblestone_from_rocks", [ns("rock")] * 4, "minecraft:cobblestone")
shaped("cooking_pot", ["I I", "III"], {"I": "minecraft:iron_ingot"}, "cooking_pot")

# world generation: loose rocks everywhere, fallen sticks in wooded biomes
def scatter(name, block, count, biomes_list):
    # 26.x: features live in worldgen/feature and have no "config" wrapper
    w(f"{D}/{NS}/worldgen/feature/{name}.json", {"type": "minecraft:simple_block", "to_place": {"id": ns(block)}})
    w(f"{D}/{NS}/worldgen/placed_feature/{name}.json", {"feature": ns(name), "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:would_survive", "state": ns(block)}]}},
        {"type": "minecraft:biome"}]})
    for i, biomes in enumerate(biomes_list):
        w(f"{D}/{NS}/neoforge/biome_modifier/{name}_{i}.json", {
            "type": "neoforge:add_features", "biomes": biomes, "features": ns(name), "step": "vegetal_decoration"})


scatter("loose_rocks", "rock", 2, ["#minecraft:is_overworld"])
scatter("fallen_sticks", "stick_on_ground", 3, ["#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "#minecraft:is_savanna"])

with open(f"{A}/lang/en_us.json") as f:
    lang = json.load(f)
lang.update({
    f"block.{NS}.rock": "Rock",
    f"block.{NS}.stick_on_ground": "Fallen Stick",
    f"block.{NS}.cooking_pot": "Cooking Pot",
    f"message.{NS}.pot_boiling": "The water will boil if the pot sits on a lit campfire or fire.",
})
w(f"{A}/lang/en_us.json", lang)
print("blocks done")

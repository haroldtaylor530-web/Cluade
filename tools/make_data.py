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
        "waterskin", "waterskin_full", "bow_drill", "flint_spear_icon", "iron_spear_icon", "diamond_spear_icon"]
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

pot_variants = {}
for low, suffix in (("false", ""), ("true", "_lowered")):
    pot_variants[f"on_campfire={low},salt=0,water=0"] = {"model": f"{NS}:block/cooking_pot{suffix}"}
    for salt in range(1, 5):
        pot_variants[f"on_campfire={low},salt={salt},water=0"] = {"model": f"{NS}:block/cooking_pot_salt{suffix}"}
    for level in range(1, 5):
        pot_variants[f"boiled=false,on_campfire={low},water={level}"] = {"model": f"{NS}:block/cooking_pot_{level}_dirty{suffix}"}
        pot_variants[f"boiled=true,on_campfire={low},water={level}"] = {"model": f"{NS}:block/cooking_pot_{level}_clean{suffix}"}
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

# ------------------------------------------------------------------ fire: realistic campfire, bow drill
cf = {}
for facing, y in (("east", 270), ("north", 180), ("south", 0), ("west", 90)):
    for lit in ("false", "true"):
        v = {"model": "minecraft:block/campfire" + ("" if lit == "true" else "_off")}
        if y:
            v["y"] = y
        cf[f"facing={facing},lit={lit}"] = v
blockstate("campfire", {"variants": cf})
w(f"{A}/items/campfire.json", {"model": {"type": "minecraft:model", "model": "minecraft:item/campfire"}})
loot("campfire", ns("campfire"))
tag(f"{D}/minecraft/tags/block/campfires.json", [ns("campfire")])
tag(f"{D}/{NS}/tags/item/kindling.json", [ns("plant_fibre"), ns("fibre_string"), "minecraft:paper", "minecraft:wheat",
    "minecraft:dead_bush", "minecraft:short_grass", "minecraft:fern", "minecraft:hay_block",
    {"id": "minecraft:short_dry_grass", "required": False}, {"id": "minecraft:tall_dry_grass", "required": False}])

# the vanilla campfire recipe now makes the realistic one: a ring of rocks, a log and two sticks
w(f"{D}/minecraft/recipe/campfire.json", {"type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": ["/L/", "RRR"],
    "key": {"/": "minecraft:stick", "L": "#minecraft:logs_that_burn", "R": [ns("rock"), "minecraft:cobblestone"]},
    "result": {"id": ns("campfire")}})
shaped("bow_drill", [" S", "//"], {"S": BIND, "/": "minecraft:stick"}, "bow_drill", cat="equipment")

with open(f"{A}/lang/en_us.json") as f:
    lang = json.load(f)
lang.update({
    f"block.{NS}.campfire": "Campfire",
    f"item.{NS}.bow_drill": "Bow Drill",
    f"entity.{NS}.falling_tree": "Falling Tree",
    f"container.{NS}.campfire": "Campfire",
    f"gui.{NS}.campfire.kindling": "Kindling",
    f"gui.{NS}.campfire.fuel": "Fuel",
    f"gui.{NS}.campfire.kindling.tip": "Kindling catches the first spark: plant fibre, fibre string, paper, wheat, dry grass, ferns or dead bushes.",
    f"gui.{NS}.campfire.fuel.tip": "Fuel keeps it burning: sticks, planks, logs or coal. Logs last a couple of minutes, coal much longer.",
    f"gui.{NS}.campfire.small": "Small",
    f"gui.{NS}.campfire.medium": "Medium",
    f"gui.{NS}.campfire.large": "Large",
    f"gui.{NS}.campfire.small.tip": "A small fire: half the fuel, a little warmth close by, cooks slowly. Rain puts it out easily.",
    f"gui.{NS}.campfire.medium.tip": "A medium fire: normal fuel use, warms you within about 5 blocks.",
    f"gui.{NS}.campfire.large.tip": "A large fire: burns fuel 2.5x as fast, warms you from 7 blocks, cooks twice as fast, lots of smoke. Don't stand in it.",
    f"gui.{NS}.campfire.light": "Light",
    f"gui.{NS}.campfire.light.tip": "Light it with a bow drill or flint and steel from your inventory. Needs kindling and fuel.",
    f"gui.{NS}.campfire.burning": "Burning",
    f"gui.{NS}.campfire.left": "%s left",
    f"gui.{NS}.campfire.embers": "Glowing embers",
    f"gui.{NS}.campfire.embers_left": "Add fuel: %s",
    f"gui.{NS}.campfire.out": "Not lit",
    f"gui.{NS}.campfire.fuel_for": "Fuel for %s",
    f"gui.{NS}.campfire.rain": "Rain will put it out",
    f"gui.{NS}.lighting.title": "Light the fire",
    f"gui.{NS}.lighting.ember": "Ember",
    f"gui.{NS}.lighting.misses": "Misses",
    f"gui.{NS}.lighting.hint": "Space or click when the spark is in the glow",
    f"gui.{NS}.lighting.wet": "It's raining: the tinder is damp",
    f"gui.{NS}.lighting.caught": "It caught!",
    f"gui.{NS}.lighting.failed": "The tinder didn't catch",
    f"message.{NS}.campfire.lit": "It's already burning.",
    f"message.{NS}.campfire.wet": "It's soaked. It won't light in water.",
    f"message.{NS}.campfire.no_kindling": "Add kindling first: plant fibre, paper or dry grass.",
    f"message.{NS}.campfire.no_fuel": "Add fuel first: sticks, logs or coal.",
    f"message.{NS}.campfire.no_starter": "You need a bow drill or flint and steel to light it.",
    f"message.{NS}.campfire.caught": "The fire caught.",
    f"message.{NS}.campfire.failed": "The tinder didn't catch. Try again.",
    f"{NS}.configuration.treeFelling": "Trees fall when you cut through the trunk",
    f"{NS}.configuration.campfireFuelRate": "Campfire fuel use",
})
w(f"{A}/lang/en_us.json", lang)
print("fire done")

# ------------------------------------------------------------------ chopped logs: a notch that deepens with each cut
WOODS_CHOP = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"]


def ring(lo, hi, uv_side):
    return {"from": [0, lo, 0], "to": [16, hi, 16], "faces": {
        d: {"uv": uv_side, "texture": "#side"} for d in ("north", "south", "east", "west")}}


for wood in WOODS_CHOP:
    name = f"chopped_{wood}_log"
    tex = {"side": f"minecraft:block/{wood}_log", "end": f"minecraft:block/{wood}_log_top",
           "inner": f"minecraft:block/stripped_{wood}_log", "cut": f"minecraft:block/stripped_{wood}_log_top",
           "particle": f"minecraft:block/{wood}_log"}
    for stage in (1, 2, 3):
        m = 2 * stage
        bottom = ring(0, 4, [0, 12, 16, 16])
        bottom["faces"]["down"] = {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": "down"}
        bottom["faces"]["up"] = {"uv": [0, 0, 16, 16], "texture": "#cut"}
        top = ring(12, 16, [0, 0, 16, 4])
        top["faces"]["up"] = {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": "up"}
        top["faces"]["down"] = {"uv": [0, 0, 16, 16], "texture": "#cut"}
        core = {"from": [m, 4, m], "to": [16 - m, 12, 16 - m], "faces": {
            d: {"uv": [m, 4, 16 - m, 12], "texture": "#inner"} for d in ("north", "south", "east", "west")}}
        w(f"{A}/models/block/{name}_{stage}.json", {"parent": "minecraft:block/block", "textures": tex,
                                                    "elements": [bottom, core, top]})
    blockstate(name, {"variants": {f"stage={s}": {"model": f"{NS}:block/{name}_{s}"} for s in (1, 2, 3)}})
    w(f"{D}/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minecraft:{wood}_log"}],
        "condition": {"type": "minecraft:survives_explosion"}}]})
tag(f"{D}/minecraft/tags/block/mineable/axe.json", [ns(f"chopped_{x}_log") for x in WOODS_CHOP])

with open(f"{A}/lang/en_us.json") as f:
    lang = json.load(f)
for wood in WOODS_CHOP:
    lang[f"block.{NS}.chopped_{wood}_log"] = "Chopped " + wood.replace("_", " ").title() + " Log"
w(f"{A}/lang/en_us.json", lang)
print("chopped logs done")

# ------------------------------------------------------------------ brick oven
# metal work moves to the oven: the iron spear and the cooking pot
for name in ("iron_spear", "cooking_pot"):
    p = f"{R}/{name}.json"
    with open(p) as f:
        r = json.load(f)
    r["type"] = "realisticsurvival:oven_shaped"
    w(p, r)

shapeless("wet_clay_brick", ["minecraft:clay_ball", ns("plant_fibre")], "wet_clay_brick")
shaped("oven_wall", ["BB", "BB"], {"B": ns("dry_clay_brick")}, "oven_wall", cat="building")

# brick piles: up to 4 bricks, two flat then two across on top
BRICKS = [([2, 0, 2], [14, 3, 7]), ([2, 0, 9], [14, 3, 14]), ([2, 3, 2], [7, 6, 14]), ([9, 3, 2], [14, 6, 14])]


def brick(frm, to):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        faces[d] = {"uv": [2, 5, 14, 11] if d in ("up", "down") else [2, 6, 14, 9], "texture": "#brick"}
    return {"from": frm, "to": to, "faces": faces}


pile = {}
for state in ("wet", "dry"):
    for n in range(1, 5):
        w(f"{A}/models/block/clay_brick_pile_{state}_{n}.json", {
            "parent": "minecraft:block/block", "textures": {"brick": f"{NS}:block/clay_brick_{state}", "particle": f"{NS}:block/clay_brick_{state}"},
            "elements": [brick(*b) for b in BRICKS[:n]]})
        pile[f"count={n},dry={'true' if state == 'dry' else 'false'}"] = {"model": f"{NS}:block/clay_brick_pile_{state}_{n}"}
blockstate("clay_brick_pile", {"variants": pile})
pools = []
for dry, item in (("false", "wet_clay_brick"), ("true", "dry_clay_brick")):
    counts = [{"type": "minecraft:set_count", "count": n,
               "condition": {"type": "minecraft:match_block", "blocks": ns("clay_brick_pile"), "state": {"count": str(n)}}}
              for n in range(2, 5)]
    pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": ns(item), "modifier": counts}],
                  "condition": {"type": "minecraft:match_block", "blocks": ns("clay_brick_pile"), "state": {"dry": dry}}})
w(f"{D}/{NS}/loot_table/blocks/clay_brick_pile.json", {"type": "minecraft:block", "pools": pools})

for formed in ("false", "true"):
    suffix = "_formed" if formed == "true" else ""
    w(f"{A}/models/block/oven_wall{suffix}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/oven_wall{suffix}"}})
blockstate("oven_wall", {"variants": {"formed=false": {"model": f"{NS}:block/oven_wall"}, "formed=true": {"model": f"{NS}:block/oven_wall_formed"}}})
loot("oven_wall", ns("oven_wall"))
w(f"{A}/items/oven_wall.json", {"model": model("block/oven_wall")})
for name in ("wet_clay_brick", "dry_clay_brick"):
    w(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    w(f"{A}/items/{name}.json", {"model": model(f"item/{name}")})
tag(f"{D}/minecraft/tags/block/mineable/pickaxe.json", [ns("oven_wall")])
tag(f"{D}/minecraft/tags/block/mineable/shovel.json", [ns("clay_brick_pile")])

with open(f"{A}/lang/en_us.json") as f:
    lang = json.load(f)
lang.update({
    f"item.{NS}.wet_clay_brick": "Wet Clay Brick",
    f"item.{NS}.dry_clay_brick": "Dried Clay Brick",
    f"block.{NS}.clay_brick_pile": "Clay Bricks",
    f"block.{NS}.oven_wall": "Oven Wall",
    f"container.{NS}.oven": "Brick Oven",
    f"message.{NS}.oven.complete": "The brick oven is complete. Light the fires and right-click a wall to work.",
    f"message.{NS}.oven.cold": "The oven is cold. Light the campfires inside.",
    f"message.{NS}.oven.broken": "The oven isn't whole any more.",
    f"message.{NS}.oven.missing.wall": "Not an oven yet: an oven wall is missing on layer %s (4x4 walls, 5 high).",
    f"message.{NS}.oven.missing.doorway": "Not an oven yet: leave a 2-wide opening in the middle of the front, layers 2 and 3 (layer %s is blocked).",
    f"message.{NS}.oven.missing.floor": "Not an oven yet: the floor inside must be gravel (layer %s).",
    f"message.{NS}.oven.missing.campfire": "Not an oven yet: put two campfires along the back inside, on the gravel (layer %s).",
    f"message.{NS}.oven.missing.inside": "Not an oven yet: the inside must be hollow (layer %s).",
})
w(f"{A}/lang/en_us.json", lang)
print("oven done")

# ------------------------------------------------------------------ food: spoilage and preserving
for name in ("jerky", "dried_fish", "dried_fruit", "rotten_food", "salt", "salt_water_cup"):
    w(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    w(f"{A}/items/{name}.json", {"model": model(f"item/{name}")})

# salt crusted in the bottom of the cooking pot: the 1-cup water model with a salt texture
for suffix in ("", "_lowered"):
    with open(f"{A}/models/block/cooking_pot_1_clean{suffix}.json") as f:
        m = json.load(f)
    m["textures"]["water"] = f"{NS}:block/pot_salt"
    w(f"{A}/models/block/cooking_pot_salt{suffix}.json", m)

# drying rack: two posts, a pole along x, little feet
def box(frm, to, tex, uv_len=None):
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in ("north", "south"):
            uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
        elif d in ("east", "west"):
            uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        else:
            uv = [frm[0], frm[2], to[0], to[2]]
        faces[d] = {"uv": uv, "texture": tex}
    return {"from": frm, "to": to, "faces": faces}


# the posts and pole use your stick texture (its wood strip is the top two rows)
def stick(frm, to, along):
    """A stick-textured box running along x, y or z."""
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        axis_of_face = {"north": "z", "south": "z", "east": "x", "west": "x", "up": "y", "down": "y"}[d]
        if axis_of_face == along:
            faces[d] = {"uv": [0, 0, 2, 2], "texture": "#stick"}  # the cut end
        elif along == "y":
            faces[d] = {"uv": [0, 0, 16, 2], "rotation": 90, "texture": "#stick"}
        elif along == "z" and d in ("up", "down"):
            faces[d] = {"uv": [0, 0, 16, 2], "rotation": 90, "texture": "#stick"}
        else:
            faces[d] = {"uv": [0, 0, 16, 2], "texture": "#stick"}
    return {"from": frm, "to": to, "faces": faces}


rack = [
    stick([1, 0, 7], [3, 16, 9], "y"), stick([13, 0, 7], [15, 16, 9], "y"),
    stick([0, 13.5, 7.25], [16, 15, 8.75], "x"),
    stick([1, 0, 5], [3, 1, 11], "z"), stick([13, 0, 5], [15, 1, 11], "z"),
]
for i, x in enumerate((3.6, 6.8, 9.8, 12.6)):
    rack.append(box([x - 0.3, 12.5, 7.6], [x + 0.3, 13.5, 8.4], "#hook"))
w(f"{A}/models/block/drying_rack.json", {"parent": "minecraft:block/block", "textures": {
    "stick": f"{NS}:block/stick_on_ground", "hook": "minecraft:block/hay_block_side",
    "particle": f"{NS}:block/stick_on_ground"}, "elements": rack})
blockstate("drying_rack", {"variants": {f"facing={f}": {"model": f"{NS}:block/drying_rack", "y": y}
                                        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
w(f"{A}/items/drying_rack.json", {"model": model("block/drying_rack")})
loot("drying_rack", ns("drying_rack"))
with open(f"{D}/minecraft/tags/block/mineable/axe.json") as f:
    axe = json.load(f)["values"]
tag(f"{D}/minecraft/tags/block/mineable/axe.json", axe + [ns("drying_rack")])

shaped("drying_rack", ["SFS", "S S", "S S"], {"S": "minecraft:stick", "F": ns("fibre_string")}, "drying_rack")
w(f"{R}/salting.json", {"type": f"{NS}:salting"})
tag(f"{D}/{NS}/tags/item/never_spoils.json", ["minecraft:honey_bottle", "minecraft:dried_kelp", "minecraft:golden_apple",
    "minecraft:enchanted_golden_apple", "minecraft:golden_carrot", "minecraft:rotten_flesh", ns("rotten_food")])

with open(f"{A}/lang/en_us.json") as f:
    lang = json.load(f)
lang.update({
    f"item.{NS}.jerky": "Jerky",
    f"item.{NS}.dried_fish": "Dried Fish",
    f"item.{NS}.dried_fruit": "Dried Fruit",
    f"item.{NS}.rotten_food": "Rotten Food",
    f"item.{NS}.salt": "Salt",
    f"item.{NS}.salt_water_cup": "Cup of Sea Water",
    f"block.{NS}.drying_rack": "Drying Rack",
    f"effect.{NS}.food_poisoning": "Food Poisoning",
    f"message.{NS}.food_poisoning": "Your stomach turns. That food was bad.",
    f"message.{NS}.salt_water": "Sea water only makes you thirstier.",
    f"message.{NS}.no_sea_water": "That's sea water. Boil it dry in a cooking pot to get salt.",
    f"message.{NS}.pot_salty": "That's sea water. Keep it boiling until it's gone and scrape out the salt.",
    f"message.{NS}.rack_full": "The rack is full.",
    f"message.{NS}.rack_shade": "It will dry slowly in here. Hang it in the sun or over a campfire.",
    f"tooltip.{NS}.keeps": "Keeps about %s",
    f"tooltip.{NS}.stage.fresh": "Fresh",
    f"tooltip.{NS}.stage.stale": "Stale",
    f"tooltip.{NS}.stage.going_off": "Going off",
    f"tooltip.{NS}.stage.rotten": "Rotten",
    f"tooltip.{NS}.left": " (about %s left)",
    f"tooltip.{NS}.days": "%s days",
    f"tooltip.{NS}.hours": "%s hours",
    f"tooltip.{NS}.cold": "Kept cold",
    f"tooltip.{NS}.salted": "Salted",
    f"tooltip.{NS}.smoked": "Smoked",
    f"{NS}.configuration.foodSpoilage": "Food spoilage",
    f"{NS}.configuration.spoilageRate": "Spoilage rate",
    f"{NS}.configuration.rawFoodRisk": "Raw and bad food can make you ill",
    f"{NS}.configuration.oreMinigame": "Ore mining game",
    f"gui.{NS}.mining.title": "Mining",
    f"gui.{NS}.mining.smashed": "Smashed! That piece is dust.",
    f"gui.{NS}.mining.got": "Got it!",
    f"gui.{NS}.mining.time": "Out of time",
    f"gui.{NS}.mining.pieces": "Ore: %s / %s pieces",
    f"gui.{NS}.mining.share": "Got: %s%%",
    f"gui.{NS}.mining.lost": "Smashed: %s%%",
    f"gui.{NS}.mining.help.move": "WASD: move",
    f"gui.{NS}.mining.help.swing": "Space: swing (breaks rock, smashes ore)",
    f"gui.{NS}.mining.help.tap": "Shift: tap (prises ore out, chips rock)",
    f"gui.{NS}.mining.help.done": "Enter: stop, keep what you got",
})
w(f"{A}/lang/en_us.json", lang)
print("food done")

# ------------------------------------------------------------------ extras: rubble slab/wall, stick fence
# Rubble uses your rock texture on every face; the fence uses your stick texture.
ROCK_TEX = f"{NS}:block/rock"
w(f"{A}/models/block/rubble_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": ROCK_TEX, "top": ROCK_TEX, "side": ROCK_TEX}})
w(f"{A}/models/block/rubble_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": ROCK_TEX, "top": ROCK_TEX, "side": ROCK_TEX}})
w(f"{A}/models/block/rubble_block.json", {"parent": "minecraft:block/cube_all", "textures": {"all": ROCK_TEX}})
blockstate("rubble_slab", {"variants": {"type=bottom": {"model": f"{NS}:block/rubble_slab"},
                                        "type=top": {"model": f"{NS}:block/rubble_slab_top"},
                                        "type=double": {"model": f"{NS}:block/rubble_block"}}})
w(f"{A}/items/rubble_slab.json", {"model": model("block/rubble_slab")})

for part in ("post", "side", "side_tall"):
    w(f"{A}/models/block/rubble_wall_{part}.json", {"parent": f"minecraft:block/template_wall_{part}", "textures": {"wall": ROCK_TEX}})
w(f"{A}/models/block/rubble_wall_inventory.json", {"parent": "minecraft:block/wall_inventory", "textures": {"wall": ROCK_TEX}})
wall_parts = [{"when": {"up": "true"}, "apply": {"model": f"{NS}:block/rubble_wall_post"}}]
for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for kind, model_name in (("low", "rubble_wall_side"), ("tall", "rubble_wall_side_tall")):
        wall_parts.append({"when": {side: kind}, "apply": {"model": f"{NS}:block/{model_name}", "y": y, "uvlock": True}})
blockstate("rubble_wall", {"multipart": wall_parts})
w(f"{A}/items/rubble_wall.json", {"model": model("block/rubble_wall_inventory")})


def bar(frm, to, along):
    """A box of your stick texture running along x, y or z; the wood strip runs the length of it."""
    length = to[{"x": 0, "y": 1, "z": 2}[along]] - frm[{"x": 0, "y": 1, "z": 2}[along]]
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        face_axis = {"north": "z", "south": "z", "east": "x", "west": "x", "up": "y", "down": "y"}[d]
        if face_axis == along:
            faces[d] = {"uv": [0, 0, 2, 2], "texture": "#stick"}  # cut end
        else:
            face = {"uv": [0, 0, min(16, length), 2], "texture": "#stick"}
            if along == "y" or (along == "z" and d in ("up", "down")):
                face["rotation"] = 90
            faces[d] = face
    return {"from": frm, "to": to, "faces": faces}


FENCE_TEX = {"stick": f"{NS}:block/stick_on_ground", "particle": f"{NS}:block/stick_on_ground"}
post = bar([6, 0, 6], [10, 16, 10], "y")
arms_north = [bar([7.5, 12, 0], [8.5, 14.5, 6], "z"), bar([7.5, 5, 0], [8.5, 7.5, 6], "z")]
w(f"{A}/models/block/stick_fence_post.json", {"parent": "minecraft:block/block", "textures": FENCE_TEX, "elements": [post]})
w(f"{A}/models/block/stick_fence_side.json", {"parent": "minecraft:block/block", "textures": FENCE_TEX, "elements": arms_north})
w(f"{A}/models/block/stick_fence_inventory.json", {"parent": "minecraft:block/block", "textures": FENCE_TEX, "elements": [
    bar([6, 0, 6], [10, 16, 10], "y"),
    bar([0, 12, 7.5], [16, 14.5, 8.5], "x"), bar([0, 5, 7.5], [16, 7.5, 8.5], "x")]})
fence_parts = [{"apply": {"model": f"{NS}:block/stick_fence_post"}}]
for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    fence_parts.append({"when": {side: "true"}, "apply": {"model": f"{NS}:block/stick_fence_side", "y": y}})
blockstate("stick_fence", {"multipart": fence_parts})
w(f"{A}/items/stick_fence.json", {"model": model("block/stick_fence_inventory")})

loot("rubble_wall", ns("rubble_wall"))
loot("stick_fence", ns("stick_fence"))
w(f"{D}/{NS}/loot_table/blocks/rubble_slab.json", {"type": "minecraft:block", "pools": [{
    "rolls": 1, "entries": [{"type": "minecraft:item", "name": ns("rubble_slab"), "functions": [{
        "function": "minecraft:set_count", "count": 2, "add": False, "conditions": [{
            "condition": "minecraft:block_state_property", "block": ns("rubble_slab"), "properties": {"type": "double"}}]},
        {"function": "minecraft:explosion_decay"}]}]}]})

shaped("rubble_slab", ["RRR"], {"R": ns("rock")}, "rubble_slab", count=6, cat="building")
shaped("rubble_wall", ["RRR", "RRR"], {"R": ns("rock")}, "rubble_wall", count=6, cat="building")
shaped("stick_fence", ["/F/", "/F/"], {"/": "minecraft:stick", "F": ns("fibre_string")}, "stick_fence", count=3, cat="building")

tag(f"{D}/minecraft/tags/block/slabs.json", [ns("rubble_slab")])
tag(f"{D}/minecraft/tags/item/slabs.json", [ns("rubble_slab")])
tag(f"{D}/minecraft/tags/block/walls.json", [ns("rubble_wall")])
tag(f"{D}/minecraft/tags/item/walls.json", [ns("rubble_wall")])
tag(f"{D}/minecraft/tags/block/fences.json", [ns("stick_fence")])
tag(f"{D}/minecraft/tags/item/fences.json", [ns("stick_fence")])
tag(f"{D}/minecraft/tags/block/wooden_fences.json", [ns("stick_fence")])
tag(f"{D}/minecraft/tags/item/wooden_fences.json", [ns("stick_fence")])
for path, extra in ((f"{D}/minecraft/tags/block/mineable/pickaxe.json", [ns("rubble_slab"), ns("rubble_wall")]),
                    (f"{D}/minecraft/tags/block/mineable/axe.json", [ns("stick_fence")])):
    with open(path) as f:
        values = json.load(f)["values"]
    tag(path, values + extra)

with open(f"{A}/lang/en_us.json") as f:
    lang = json.load(f)
lang.update({f"block.{NS}.rubble_slab": "Rubble Slab", f"block.{NS}.rubble_wall": "Rubble Wall",
             f"block.{NS}.stick_fence": "Stick Fence"})
w(f"{A}/lang/en_us.json", lang)
print("extras done")

# Realistic Survival (NeoForge, Minecraft 26.3)

Makes survival harder and more grounded, and adds throwable spears.

## Install

Put `realisticsurvival-<version>.jar` in your `mods` folder (NeoForge 26.3). Settings are in
`config/realisticsurvival-common.toml`, or in game under Mods → Realistic Survival → Config.

## What it adds

**Thirst.** A row of water drops above the food bar; a full bar lasts about 16 minutes at rest,
less when sprinting, sweating in the heat or in the Nether. Low thirst stops sprinting, weakens
you and slows healing; at zero you take damage.
- Sneak and right-click water with an empty hand to drink a handful.
- Water from ponds, lakes, swamps and the sea is **dirty** and often gives you dysentery
  (you lose water and food fast). River water, mountain springs (above y=110), rain
  (look up while it's raining) and cauldron water are clean.
- Clay cup: 3 clay balls in a V, then fire it. Fill it from water; boil a dirty cup in a
  furnace, smoker or on a campfire.
- Waterskin: leather and string; holds 4 drinks.
- Water bottles count as dirty water. Soups, milk, melon and fruit help a little; bread,
  cookies and dried kelp make you a bit thirstier.

**Body temperature.** A gauge above the armour bar (blue cold, green fine, red hot). Biome,
time of day, altitude, rain, swimming, caves, nearby fires/lava/furnaces and your clothes all
count. Deserts are scorching by day and mild at night; snowy biomes at night will kill you
without warm clothes or a fire. Too cold: hypothermia (slow, weak mining, frost on the screen,
then damage). Too hot: heatstroke (weaker, thirstier, nausea, then damage).
- Fur pelts drop from wolves, foxes, polar bears, llamas, camels (and sometimes rabbits);
  craft a fur hat, coat, trousers and boots like normal armour. Leather is warm too.

**Injuries.**
- Hard falls can break your leg: very slow, can't jump or sprint, for 10 minutes. A splint
  (stick, string, stick) cuts that to 3 minutes of a slight limp.
- Cuts, bites and arrows can make you bleed (half a heart every few seconds). Bandages
  (wool + string, or 2 paper + string) stop it.

**A harder start, harder work.**
- You can't get logs by punching; you need an axe. Punching wood or stone hurts.
- Get sticks from leaves, plant fibre from grass (much more with a flint knife), and flint
  from gravel. Right-click flint on stone to knap it into shards.
- Fibre string: 3 plant fibre. Flint knife, hatchet and pickaxe from flint, string and sticks.
- Logs give 2 planks by hand instead of 4.
- Mining and chopping make you hungrier and thirstier.

**Loose rocks and fallen sticks** lie on the ground (rocks everywhere, sticks in forests,
taigas, jungles and savannas). Punch them to pick them up. 4 rocks make a cobblestone, so you
can make stone tools without a pickaxe.

**Cooking pot** (5 iron ingots in a U). Put it on a lit campfire, fill it with a water
bucket, water bottles or dirty cups, and after about 15 seconds the water boils clean (steam
rises). Take clean water out with clay cups, a waterskin or bottles.

**Spears.** Flint, iron and diamond. Stab with them (slow, longer reach), or hold right-click
to wind up and release to throw. The longer the wind-up (up to a second), the harder the throw.
Thrown spears can make targets bleed. Walk over a landed spear to pick it back up.
Recipe: tip (flint shard / iron ingot / diamond) top right, string next to it, two sticks
down the diagonal.

## Replacing the art

To regenerate everything, run in order: `tools/make_art.py` (pixel art), `tools/make_data.py`
(item models, recipes, tags, text) and `tools/convert_bbmodels.py` (your Blockbench models).

**Spear models** (Blockbench, *Java Block/Item*): replace
`src/main/resources/assets/realisticsurvival/models/item/<flint|iron|diamond>_spear.json` and the
texture `textures/item/<material>_spear.png`. Lay the spear along the X axis with the **tip
pointing east (+X)**; it can stick out of the 16×16 box (−16 to 32 is allowed). The same model
is used in your hand and in flight. Set the hand poses in Blockbench's Display tab. The
inventory icon is the flat `<material>_spear_icon.png`.

## Building

Needs Java 25. Run `gradlew build`; the jar is in `build/libs`. GitHub Actions builds every
push to the `realistic-survival` branch.

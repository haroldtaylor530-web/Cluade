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

**Realistic campfires.** The campfire recipe now makes a fire pit (stick, log, stick over
three rocks or cobblestone). It is built cold:
- Put **kindling** (plant fibre, fibre string, paper, wheat, dry grass, ferns, dead bushes) and
  **fuel** (sticks, planks, logs, coal) in it: right-click with them, or open its menu with an
  empty hand. A log burns about 2 minutes on a medium fire, coal about 6.
- **Light it** with a **bow drill** (string top right, two sticks along the bottom) or flint and
  steel: right-click the fire with it, or press Light in the menu. A little game pops up: press
  space or click while the swinging spark is in the glowing patch. Flint and steel needs 3
  good strikes, a bow drill 4 with a smaller patch; rain makes it harder; 3 misses and the
  tinder doesn't catch.
- **Small / Medium / Large** in the menu: bigger fires burn fuel faster (half, normal, 2.5x),
  warm you from further away (about 3, 5 and 7 blocks), cook faster, smoke more, and a large
  fire sets you alight if you stand in it.
- When the fuel runs out it dies down to **embers** for 45 seconds; add fuel in time and it
  catches again by itself.
- **Rain** puts it out unless it has a roof over it (small fires go out fastest). Water or a
  shovel douses it. Food cooks on it and the cooking pot works on it as before.

**Tree felling.** Chop a tree's trunk with an axe and each cut takes a layer off: the log gets
a notch that goes deeper three times, down to the core. Cut the core and the whole tree
topples over away from you, crashes down and drops its logs, saplings and sticks where it
lands. Anything it lands on gets hurt. Better axes cut faster and take more off each time: flint and wooden axes need 4 cuts,
stone, copper and iron 3, diamond, netherite and gold 2. Thick 2x2 trunks are slower to cut. Sneak to cut a
single log, and player-built log walls (with placed leaves) never fall.

**Brick oven.** Furnaces, work stations and all metal work need a brick oven; the crafting
table can't make them any more. That covers the furnace, smoker, blast furnace, stonecutter,
anvil, grindstone and smithing table; iron, gold and copper tools, weapons, spears and armour
(and the shield); and metal goods such as buckets, shears, rails, cauldrons, hoppers, chains,
lanterns, iron doors, trapdoors and bars, minecarts, compasses, clocks, lightning rods and the
cooking pot. Flint and steel and stone tools are still made by hand.
- **Clay bricks:** a clay ball and a plant fibre make a wet clay brick. Place it on the ground
  (up to 4 to a pile) and it dries in 1 to 3 days; rain soaks it and holds it back. Break the
  pile to collect the dried bricks.
- **Oven wall:** 4 dried bricks in a square.
- **The oven:** a 4x4 ring of oven walls, 5 high, open at the top. Leave a 2-wide opening in
  the middle of the front on the 2nd and 3rd layers. Inside, the floor is gravel and two
  campfires go along the back. When it's complete the walls darken.
- **Using it:** light at least one of the campfires, then right-click any wall to open the
  oven's 3x3 workbench. It uses the normal crafting patterns. If something is wrong,
  right-clicking a wall tells you what's missing.
- A full oven takes 56 wall blocks, which is 224 bricks.

**Food spoils.** Food goes stale, then starts going off, then rots, and turns into rotten
flesh (meat and fish) or rotten food (everything else, good for the composter). A coloured bar
under the item and its tooltip show how fresh it is. Roughly: raw meat 3 days, raw fish 2,
cooked meat 5, bread 10, apples 2 weeks, carrots and potatoes 3 weeks. Dried kelp, honey and
golden food never spoil. Food gathered within the same quarter-day still stacks.
- **Eating:** stale food fills you a quarter less, food that's going off half as much and can
  give you **food poisoning** (you burn through food and water for a minute, with nausea).
  Raw meat and fish can make you ill too: raw chicken often, pork sometimes, beef, mutton,
  rabbit and fish now and then. Cook it.
- **Flint striker** (a flint and a rock, shapeless). A single-use fire lighter for before you have iron: it works like flint and steel
  in the campfire lighting game and then breaks. Its icon is cut from your rock texture.
- **Fire starter** (2 plant fibre, 1 binding, 1 stripped log, shapeless). The best kindling and fuel: as kindling it makes the lighting game
  easier (one hit fewer, a sweet spot half as wide again), and as fuel it burns 4 minutes on a medium fire (a log burns 2).
- **Tools go dull** (config `toolDulling`). Pickaxes, axes, shovels and hoes lose their edge a point per block mined (flint, wood and
  stone fastest, iron slower, diamond slowest). A dull tool digs up to 45% slower, also in the ore mining game. Make a **whetstone**
  from 2 rocks (it uses your rock texture), hold it in one hand and the tool in the other, and right-click: +35 edge per go,
  48 uses. The tooltip shows how keen the edge is.
- **Stamina** (config `stamina`). A thin bar under the water drops shows up when you're not fully rested. Sprinting empties it in
  about 22 seconds, swimming drains it slowly, mining drains a little per block. At zero you're out of breath: no sprinting and
  slower digging until it's back above 6. Standing still recovers fastest; hunger and thirst slow the recovery.
- **Rubble slab, rubble wall, stick fence** (small building blocks). Rubble is made from rocks (3 across for 6 slabs, 2 rows of 3
  for 6 walls) and uses your rock texture on every face. The stick fence is 3 per crafting (sticks plus a fibre string) and uses
  your stick texture. Stick fences connect to vanilla fences.
- **Drying rack** (sticks and a fibre string; it's built from your stick texture). Right-click to hang meat, fish or fruit (4
  hooks, up to 4 pieces each), empty hand to take it down.
  - **Over a campfire** (directly below, or two below with air between): raw meat and fish
    come out **cooked and smoked** in a quarter of a day; smoked meat keeps 8 times as long.
    Fruit dries.
  - **In the sun** on a dry day: meat turns to **jerky**, fish to **dried fish**, fruit to
    **dried fruit** in about a day (each keeps about 40 days). In the shade or at night it dries
    four times slower, and rain soaks it and undoes some of the work. Meat keeps ageing while it
    hangs, so don't leave it too long.
- **Salt:** fill a clay cup in the sea, pour it into a cooking pot on a fire and let it boil
  dry; each cup leaves a salt to scrape out with an empty hand. Sea water won't go in a
  waterskin and drinking it makes you thirstier. **Salting:** one salt with up to 8 pieces of
  the same meat or fish in a crafting grid salts them all; salted meat keeps 6 times as long and
  is less risky raw.
- **Cold storage:** food in a chest or barrel ages much slower when it's next to ice or snow
  blocks (4 to 7 times slower), in a snowy biome (2.5 times) or in a cellar well below ground
  (a bit slower).

**Mining ore is a little game.** Hit any ore with a pickaxe that can mine it and you see the
ore up close, its own texture with one square per pixel. Your pickaxe starts outside the block
and walks round its edge and along any tunnel you dig.
- **WASD / arrows:** move (or turn, if the way is blocked). The outlined square is what you'll hit.
- **Space:** swing hard. Breaks rock (a better pickaxe or Efficiency breaks more per swing;
  deepslate is tougher, netherrack softer), but hitting ore this way smashes that piece to dust.
- **Shift:** tap gently. Prises a whole piece of ore out cleanly; only chips rock.
- **Enter / Esc:** stop and keep what you've got. You have 45 seconds.
- You get the share of the ore you got out: all of it gives the full drop (Fortune still
  works), half of it about half. Longer digs wear the pickaxe more. Turn it off with
  `oreMinigame` in the config.

**Spears.** Flint, iron and diamond. Stab with them (slow, longer reach), or hold right-click
to wind up and release to throw. The longer the wind-up (up to a second), the harder the throw.
Thrown spears can make targets bleed. Walk over a landed spear to pick it back up.
Recipe: tip (flint shard / iron ingot / diamond) top right, string next to it, two sticks
down the diagonal.

## Replacing the art

To regenerate everything, run in order: `tools/make_art.py`, `tools/make_fire_art.py`, `tools/make_oven_art.py`,
`tools/make_food_art.py` (pixel art), `tools/make_data.py`
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

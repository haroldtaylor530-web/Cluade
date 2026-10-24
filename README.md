# Corpse Decay (Forge 1.20.1)

When a mob dies it doesn't vanish in a puff of smoke. Its body ragdolls to the ground and stays
there, rotting through stages down to a bare skeleton, still wearing whatever it had on.

## What happens

- **Ragdoll.** The body is thrown by the killing blow and tumbles under real physics: it
  topples the way it was hit (four-legged animals roll onto their side), bounces, slides and
  settles against the actual shape of the blocks around it: stairs, slabs, fences, ledges.
  Legs, arms, heads, tails and wings swing loose and come to rest on the ground. Hitting a body
  knocks it around again, and explosions throw bodies.
- **Decay.** Bodies go pale, then bloat green and purple, turn black and wet with flesh and bone
  showing through, dry out to leathery scraps, and end as a bare skeleton. Flies buzz around
  them while they rot. By default it takes one Minecraft day (20 minutes) to reach the bone.
- **Burnt bodies.** A mob that dies burning (a zombie caught in the sun, fire, lava edges, fire
  aspect, flaming arrows) leaves a charred body: it starts at the stage just before the
  skeleton, blackened and cracked with embers glowing in the cracks, scorched bones showing
  through and clothes, wool and fur burnt away (armour survives). It burns for a few seconds,
  then smokes for a while. A body that catches fire later, from a fire block or flint and steel,
  chars the same way.
- **Different remains for different deaths.**
  - Drowned: pale, waxy and waterlogged, and the body floats.
  - Frozen (powder snow): frosted over with ice crystals, and rots four times slower.
  - Blown up: torn apart, with some limbs missing.
  - Withered (the wither effect, the Wither, wither skeletons): a shrivelled grey-black husk.
  - Fell to its death: crumpled, limbs bent at broken angles.
  - Crushed by an anvil or falling block: squashed flat where it stood.
- **Mummies.** A body that dies in a swamp or mangrove swamp doesn't rot to bones: the peat tans
  it into a bog body, dark brown and leathery, shrunken into creases, with its skin and clothes
  still on. A body that dies in a frozen biome freeze-dries the same way, into grey-brown
  parchment with frost on it and bone showing where the skin has split. Mummies don't bloat or draw
  flies and last four times as long as a skeleton. Burnt, blown-up and withered bodies don't
  mummify. Which biomes do it is the tags `#corpsedecay:mummifies_in_bog` and `mummifies_in_ice`.
- **Remains of the long dead.** New chunks can hold the bones of things that died long ago, lying
  where they fell. Adventurers lie in their battered armour with their sword, bow or pickaxe, old
  arrows still in them, sometimes with a chest of their belongings beside them; animals lie as
  bare bones. What's on and around them depends on where they are: moss in lush caves, mushrooms
  in mushroom fields and dark forests, cobwebs in caves, dripstone in dripstone caves, dead bushes
  and cactus in the desert, snow in the cold, roots and fungus in the nether. The bones weather to
  match: yellowed, grimy, sandy, sun-bleached, mossy, charred in the nether, stained purple in the
  end. They turn up on the surface, in caves, in the nether (adventurers, piglins, hoglins,
  striders) and in the end (heroes the dragon got, endermen). You can take the gear and arrows off
  them. They never crumble and don't count towards the corpse limit; sneak and hit them to break
  them up for bones. Which mobs turn up is the tags `#corpsedecay:remains_mobs`,
  `nether_remains_mobs` and `end_remains_mobs`.
- **Old mummies.** Swamps hold bog bodies from long ago, sunk in patches of mud or under the water
  with lily pads over them: people in skin caps and leather capes, and now and then an animal.
  Frozen biomes hold freeze-dried hunters in their furs with a flint hand axe or a bow, sometimes
  with an arrow still in their back, and animals caught by the cold. Some lie in the snow and
  some are melting out of a sheet of clear ice, frozen into it up to their middle. Break one up for its dried skin (leather) and
  bones.
- **Whale falls.** Somewhere in the oceans, rarely, whales have gone down to die on the same stretch
  of sea floor, and their full-size skeletons lie there together: blue whales, humpbacks with their
  long flippers, sperm whales with their great basin skulls and toothed jaws, and small beaked
  whales with long narrow beaks and crests round the blowhole. They're drawn bone by bone
  (vertebrae, curved ribs, skull plates, finger bones in the flippers) rather than built from
  blocks, and the bones are stained yellow-brown and oily. Each whale is at its own stage: some
  still have pale scraps of flesh on the head and in the ribcage, furred white with bacteria and
  swarming with hagfish; some only shreds, with red bone worms growing out of the bones; some are
  bare, paler bones coated in white and yellow mats. Octopuses sit on the bones, hagfish and
  eelpouts weave through them, loose bones lie scattered round about, sea pickles glow, and white,
  yellow and orange bacterial mats spread over the mud. Sneak and hit one to break it up for bones
  and bone blocks.
- **Pirate crews.** Every shipwreck, in the sea or beached on a shore, has some of its crew still
  aboard: three to six old bodies lying on the decks and in the hold in a pirate's clothes. There is
  usually a captain in a dark coat and tricorn hat with a cutlass and a spyglass or compass; the hands
  have bandanas in faded cloth, shirts, breeches, boots, cutlasses, boarding axes, bows and crossbows,
  all battered and dyed by the sea. Under water they have sea pickles and dead coral growing on them
  and some are drowned. Like all old remains you can take their gear off them. No blood.
- **Ghost ships.** Now and then, out in the frozen oceans, there is a ship that never came home: a
  merchant brig still whole, sails set and stiff with frost, locked fast in the pack ice with snow
  on her decks. Her crew froze where they stood: the helmsman with his hands on the wheel, the mate
  alone at the bow with his spyglass raised, men leaning on the rail and sitting hunched against the
  mainmast, the cook slumped at the galley table below, others still in their bedrolls in the hold.
  The captain sits at the chart table on the quarterdeck, and his log lies open in front of him in
  an invisible item frame. Take it and read how they came to be there. Blue soul lanterns still
  burn in the cabin and the hold; the captain's sea chest and the ship's stores can be looted.
- **The valley of whales.** In deserts lie early whales turned to stone, like the ones in Egypt's
  Wadi Al-Hitan: long, snake-like basilosaurs half buried in the sand, still with their tiny hind
  legs, among wind-carved pillars of sandstone, with shark teeth and shells in the sandstone
  beneath. Their bones are a new block, fossilised bone.
- **Rock formations.** Most fossils lie in formations: great lenses of layered rock, each modelled
  on a real fossil site and holding the fossils of its time, found where that rock belongs. Where
  one meets a cave, a cliff or a hillside you can see its layers.

  | Formation | Age | Rock | Where | Fossils |
  |---|---|---|---|---|
  | Burgess Shale | Cambrian | dark shale with pale bands, steeply tilted | inside mountains | Cambrian sea creatures, anomalocaris, trilobites |
  | Reef Limestone | Devonian | limestone and calcite | under warm seas | sea lilies, trilobites, shells, fish, shark teeth |
  | Coal Measures | Carboniferous | sandstone, shale and coal seams | under swamps, forests, jungles | ferns, leaves, giant dragonflies, standing stone trunks, early tetrapods |
  | Red Beds | Triassic–Jurassic | red sandstone and banded mudstone | badlands, savanna plateaus | long necks, plated and sail backs, hunters, nests, footprints, stone logs |
  | Blue Lias | Jurassic | grey limestone and shale | coasts and seas | ammonites, fish lizards, sea serpents |
  | Solnhofen Limestone | Jurassic | thin limestone slabs | under plains and forests | fliers, feathers, dragonflies, crabs, jellyfish |
  | Chalk | Cretaceous | white chalk with bands of flint | coasts and chalk downs | sea urchins, shark teeth, ammonites, sea reptiles, turtles |
  | Hell Creek | Late Cretaceous | sandstone, mudstone and lignite | plains and savannas | tyrants, horned and duckbilled dinosaurs, raptors, amber |
  | Flaming Cliffs | Cretaceous | orange sandstone | under deserts | raptors, horned dinosaurs, nests of eggs |
  | Fossil ash beds | Eocene | volcanic tuff, white ash, paper shale | windswept savannas, meadows | leaves, insects, feathers, terror birds, stone stumps |
  | Permafrost | Ice Age | frozen silt, ice lenses, gravel | snowy biomes, near the surface | mammoths, sabre-tooths |

  Tar pits in savannas, plains and deserts hold the blackened skeletons of the animals they
  trapped; tar is slow to walk through. New rocks come with them: shale, limestone, chalk, chalk
  with flint (mine it for flint), coal seams (mine them for coal), lignite (burns as fuel),
  permafrost and tar. Outside the formations fossils are rare.
- **Fossils in the rock.** Whole skeletons of fossil blocks are set into the rock underground, seen
  side-on like a museum fossil, so you find them mining or exposed in cave walls and badlands
  cliffs. There are 18 kinds: long-necked giants, tyrants, raptors with sickle claws, horned and
  crested plant eaters, plated and sail-backed beasts, long-necked sea serpents, fish lizards,
  fliers with raised wings, sabre-toothed cats, mammoths with tusks, turtles, serpents, whales,
  terror birds and more, and no two are quite the same length. They're built from 20 kinds of
  bone: five kinds of skull, curving neck pieces, backbone, ribcage, back plates, shell, tail and
  tail tip, wing, tusk, legs, feet, claws and flippers.
- **Bone beds**: jumbles of loose bones and shells where lots of things died together.
- **Small fossils** turn up on their own like ores: ammonites, trilobites, fish, ferns, scallops,
  snails, crabs, starfish, sea urchins, sea lilies, dragonflies, feathers, leaves and shark teeth.
- **Footprint trails** cross bare rock on the surface (badlands, stony peaks and shores) and cave
  floors, left, right, left.
- **Petrified trees**: fallen trunks and standing stumps turned to stone, with lumps of amber
  against them, sometimes with a mosquito or beetle caught inside. Amber turns up on its own too.
- **Buried nests**: sealed hollows in the rock with clutches of fossil eggs, sometimes with the
  parent's skeleton in the wall.
- **Dig sites** in deserts, badlands and savannas: an abandoned pit beside a half-dug skeleton, with
  a ladder, scaffolding, suspicious sand or gravel to brush (fossils, amber and eggs inside), a camp
  with torches, a chest of the diggers' things and a cold campfire, and now and then the digger.
- Every fossil comes in stone, deepslate, sandstone, red sandstone and terracotta to match the rock
  it's in. Mine one with a pickaxe to get the block itself and build your own display; they're all
  in their own creative tab. Fossil eggs stack up to three in a clutch like turtle eggs.
- **The Cambrian.** Deep down in the oldest rock lie Cambrian beds: thin layers crowded with the
  strange sea creatures of half a billion years ago: five-eyed opabinia, spiny hallucigenia and
  wiwaxia, lacy marrella, little pikaia (one of the first animals with a backbone), jellyfish,
  reef-building archaeocyaths, worm burrows and trilobites, and now and then the great hunter of
  that sea, anomalocaris, two blocks long. The smaller ones also turn up on their own below y 8.
- **The Neolithic (New Stone Age).**
  - Painted caves: aurochs, deer, horses, mammoths, hunters with spears, handprints and spirals in
    red and yellow ochre and charcoal on cave walls, with a cold hearth below and sometimes the
    painter's remains.
  - Stone circles: rings of standing stones, some fallen, sometimes with a dolmen or a hearth in
    the middle and a grave beneath.
  - Dolmens: a great capstone on standing stones, over a burial.
  - Barrows: grassy burial mounds with a sealed stone chamber inside holding the dead with their
    flint axe, pots and a chest of grave goods (flint, bone, ochre, wheat, clay, pottery sherds).
  - Mammoth-bone huts on snowy plains: a ring of piled bones and hides around a hearth.
  - New things from the time: the **flint hand axe** (a stone-tier axe: two flint and a stick),
    the **bone flute** (bone and flint; play it, looking up for high notes and down for low ones)
    and **ochre** (crafts into red dye). Cave paintings can be mined and hung up too.
- **Real skeletons for every mob.** The skeleton is built from each mob's own model: a skull with
  eye sockets and teeth, a ribcage and spine running the length of the body (upright for zombies
  and villagers, horizontal for animals), a pelvis, leg bones with knobbly joints, tail
  vertebrae, wing bones, fin rays. Horns and hooves stay. It works for modded mobs too.
- **Gear stays on.** Armour, held weapons and tools, saddles, horse armour, llama carpets,
  villager outfits, and clothes painted onto mobs like zombies, illagers and witches all stay on
  the body through every stage. Clothes get dirtier and tear as the body rots, and a skeleton
  ends up wearing them.
- **Arrows stay in.** Every arrow that hit the mob stays stuck in the body at the exact spot and
  angle it went in. Arrows shot into a corpse stick too.
- **Looting.** Right-click a body to take off whatever the mob would normally have dropped, and
  pull out arrows you can pick back up. Gear the mob wouldn't have dropped stays on it.
- **Clearing up.** Sneak and hit a body to break it up. Skeletons crumble away after an hour
  (configurable, or never).

Different kinds of mobs break down differently:

| Mob | What's left |
|---|---|
| Most mobs | Flesh rots down to a skeleton |
| Skeletons, strays, wither skeletons, skeleton horses | Already bones, they just lie there |
| Spiders, silverfish, endermites, bees, blazes, ghasts, vexes, allays, snow golems | No skeleton: they shrivel to a husk and fade away |
| Squid, glow squid | Soft all through: the body rots away and leaves only the hard parts, a small dark hooked beak and the long thin leaf of the pen, which lie there like bones until they crumble |
| Iron golems, shulkers | Don't rot |
| Ender dragon, wither, slimes, magma cubes, armour stands | No corpse |

These are entity type tags (`#corpsedecay:no_corpse`, `already_bones`, `no_skeleton`,
`does_not_rot`, `beak_and_pen`), so a datapack can change them or add modded mobs.

## Settings

`config/corpsedecay-common.toml`:

| Setting | Default | |
|---|---|---|
| `enabled` | true | Leave corpses at all |
| `onlyPlayerKills` | false | Only for mobs a player killed |
| `minutesToSkeleton` | 20 | Real minutes from fresh body to bare bone |
| `skeletonMinutes` | 60 | How long skeletons last before crumbling (0 = forever) |
| `maxCorpses` | 60 | Most corpses per dimension; the oldest crumble first (stops mob farms lagging) |
| `ragdollForce` | 1.0 | How hard killing blows throw bodies |
| `lootAllEquipment` | false | Let players take everything off a body, not just what would have dropped |
| `flies` | true | Flies around rotting bodies |
| `mummify` | true | Bodies in swamps and frozen biomes mummify instead of rotting to bones |
| `remains` | true | Old remains, mummies, whale falls, shipwreck crews and ghost ships in newly generated chunks |
| `fossils` | true | Fossils in the rock and stone whales in the desert, in newly generated chunks |
| `ancientSites` | true | Stone Age sites in newly generated chunks |
| `excludedMobs` | [] | Mob ids that never leave a corpse |

## Installing

Needs Minecraft 1.20.1 and Forge 47 or newer. Put the jar in `mods`. In multiplayer it must be
on the server and on every player's game.

## License

MIT. See [LICENSE](LICENSE).

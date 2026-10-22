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
| Spiders, silverfish, endermites, bees, squid, blazes, ghasts, vexes, allays, snow golems | No skeleton: they shrivel to a husk and fade away |
| Iron golems, shulkers | Don't rot |
| Ender dragon, wither, slimes, magma cubes, armour stands | No corpse |

These are entity type tags (`#corpsedecay:no_corpse`, `already_bones`, `no_skeleton`,
`does_not_rot`), so a datapack can change them or add modded mobs.

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
| `excludedMobs` | [] | Mob ids that never leave a corpse |

## Installing

Needs Minecraft 1.20.1 and Forge 47 or newer. Put the jar in `mods`. In multiplayer it must be
on the server and on every player's game.

## License

MIT. See [LICENSE](LICENSE).

# NEXORA: BEASTS — Beast System Foundation

This document describes the current playable foundation of the creature system.

## Included

- Persistent player collection stored by UUID in `players.yml`.
- Ten rarity tiers: common, uncommon, rare, epic, legendary, mythic, ancient, divine, forbidden, absolute.
- Two acquisition paths:
  - Use a NEXORA egg (right-click air or a block). Hatching takes five seconds; each player can hatch one egg at a time.
  - Interact with a living non-player entity while holding a NEXORA capture sphere. The creature is recorded in the collection and removed from the world.
- Active team with three saved slots.
- Creature inspection and release.
- Individual vitality, power and defense values derived from rarity, level and saved potential.
- Training: spend one wheat for 25 XP; each level-up improves stats. Maximum level is 100.
- Admin test commands for eggs and capture spheres.
- Rarity-colored egg names and lore; unique egg textures are not included and require a Bedrock resource pack.

## Commands

Player:
- `/beasts` or `/beasts help`
- `/beasts eggs`
- `/beasts collection`
- `/beasts team`
- `/beasts team <1-3> <creature-id>`
- `/beasts info <creature-id>`
- `/beasts train <creature-id>` — spend one wheat for 25 XP.
- `/beasts release <creature-id>`

Operator:
- `/beasts giveorb <player> [amount]`
- `/beasts giveegg <player> <species|random> <rarity>`
- `/beasts giveeggs <player>` — give one preview egg for each rarity.

Rarity identifiers: `common uncommon rare epic legendary mythic ancient divine forbidden absolute`.

## Current limitations

- Captured creatures are collection records, not summoned companion entities.
- The active three-slot team is saved but is not connected to battles.
- No battle system, abilities, evolution, custom models, or region-specific spawn rules yet.
- Egg rarity is defined by the egg's lore metadata; wild capture uses weighted rarity probabilities.
- In-game compatibility must still be verified on the exact server build after installing the generated JAR.

# NEXORA: BEASTS — Beast System Foundation

This document describes the first playable foundation of the creature system.

## Included in this implementation

- Persistent player collection stored by UUID in `players.yml`.
- Ten rarity tiers: common, uncommon, rare, epic, legendary, mythic, ancient, divine, forbidden, absolute.
- Two acquisition paths:
  - Hatch a NEXORA egg by using it (right-click air or a block).
  - Tame a living non-player entity by interacting with it while holding a NEXORA capture sphere.
- Active team with three slots.
- Creature inspection and release commands.
- Admin commands to issue test eggs and capture spheres.
- A ten-tier egg catalog with rarity-colored custom names and lore.
- `/beasts giveeggs <player>` issues one preview egg for each rarity tier.

## Commands

Player:
- `/beasts` or `/beasts help`
- `/beasts collection`
- `/beasts team`
- `/beasts team <1-3> <creature-id>`
- `/beasts info <creature-id>`
- `/beasts release <creature-id>`
- `/beasts eggs`

Operator:
- `/beasts giveorb <player> [amount]`
- `/beasts giveegg <player> <species|random> <rarity>`
- `/beasts giveeggs <player>` — give a showcase set of all ten egg rarities.

Rarity identifiers: `common uncommon rare epic legendary mythic ancient divine forbidden absolute`.

## Current scope and limitations

This is the first foundation, not the complete final gameplay loop. Captured entities are recorded as collection entries and removed from the world; they are not yet summoned as custom companion models. Eggs grant a creature directly to the collection on use. The ten rarities are distinguished in their custom display names and lore; unique egg artwork still requires a matching Bedrock resource pack and is not included in this build. Capture rarity is rolled using weighted probabilities. The active three-slot team is saved, but battle, stats, abilities, leveling progression, custom forms and region-specific spawn rules are future systems.

The plugin targets the Nukkit-MOT Maven dependency already used by this repository. A successful GitHub Actions build and in-game test on the exact server build are still required before treating the JAR as production-ready.

# NEXORA: BEASTS — Beast System Foundation

This document describes the current playable foundation of the creature system.

## Included

- Persistent player collection stored by UUID in `players.yml`.
- Touch-friendly Nukkit-MOT form menus: main screen, collection, creature cards, team slots, egg archive, training and release confirmation.
- `/beasts` opens the graphical menu for players; text commands remain available for administration and compatibility.
- Ten rarity tiers: common, uncommon, rare, epic, legendary, mythic, ancient, divine, forbidden, absolute.
- Two acquisition paths:
  - Use a NEXORA egg (right-click air or a block). Hatching takes five seconds; each player can hatch one egg at a time.
  - Interact with a living non-player entity while holding a NEXORA capture sphere. The creature is recorded in the collection and removed from the world.
- Active team with three saved slots.
- Summon the active team as visible in-world companion entities; companions are re-created on player join and follow by teleporting back when they fall more than five blocks behind.
- Recall companions to the collection from the team menu or with `/beasts recall`.
- Creature inspection and release.
- Individual vitality, power and defense values derived from rarity, level and saved potential.
- Training: spend one wheat for 25 XP; each level-up improves stats. Maximum level is 100.
- Admin test commands for eggs and capture spheres.
- Rarity-colored egg names and lore; the first GUI pass uses native Bedrock form windows and does not depend on client-side resource-pack assets yet.

## Commands

Player:
- `/beasts` or `/beasts help`
- `/beasts eggs`
- `/beasts collection`
- `/beasts team`
- `/beasts team <1-3> <creature-id>`
- `/beasts summon` — summon the active team.
- `/beasts recall` — dismiss summoned companions.
- `/beasts info <creature-id>`
- `/beasts train <creature-id>` — spend one wheat for 25 XP.
- `/beasts release <creature-id>`

Operator:
- `/beasts giveorb <player> [amount]`
- `/beasts giveegg <player> <species|random> <rarity>`
- `/beasts giveeggs <player>` — give one preview egg for each rarity.

Rarity identifiers: `common uncommon rare epic legendary mythic ancient divine forbidden absolute`.

## Current limitations

- Companions are visual world entities; their combat behavior and battle integration are not implemented yet.
- Companions are removed on logout and summoned again after the player rejoins. The exact entity support depends on the Nukkit-MOT entity registry.
- No battle system, abilities, evolution, custom models, or region-specific spawn rules yet.
- The dedicated NEXORA visual resource pack (custom egg/orb textures and menu artwork) is a separate deliverable and is not yet bundled in this build.
- Egg rarity is defined by the egg's lore metadata; wild capture uses weighted rarity probabilities.
- In-game compatibility must still be verified on the exact server build after installing the generated JAR.

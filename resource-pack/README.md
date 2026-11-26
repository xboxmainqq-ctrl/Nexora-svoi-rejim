# NEXORA BEASTS — Bedrock Resource Pack

The pack now contains three isolated custom companion prototypes:
- `nexora:beast_wolf` — custom model, rune texture, idle and walk animations
- `nexora:beast_fox` — custom pointed-ear model, bushy tail, orange/cream rune texture and animations
- `nexora:beast_cat` — custom slim model, upright tail, dark-violet/cyan texture and animations

The Nukkit-MOT plugin registers matching network identifiers and reuses each
vanilla server entity implementation for movement and base behavior. Only
NEXORA companions use these custom identifiers; ordinary wild wolves, foxes
and cats keep their vanilla appearance.

## Build

Run `python3 resource-pack/build_pack.py`. It creates
`resource-pack/build/NEXORA_Beasts_RP.mcpack`.

GitHub Actions publishes the plugin JAR and resource pack as separate
artifacts. The resource-pack artifact is a ZIP containing the `.mcpack`;
extract the ZIP first, then import or install the contained pack.

## Server installation

The `.mcpack` is a Bedrock resource pack, not a plugin JAR. Install it through
the Nukkit-MOT resource-pack configuration and require clients to accept the
server pack. Keep the vanilla fallback until the custom entities have been
tested on the exact server and client versions.

These are still prototypes. The remaining ten species, rarity-specific
appearances/effects, and live in-game rendering/animation tests are not
complete yet.

# NEXORA BEASTS — Bedrock Resource Pack

This pack currently contains the first isolated custom-entity prototype:
`nexora:beast_wolf`.

The Nukkit-MOT plugin registers the matching network identifier and uses the
existing wolf server implementation for movement/combat. Only NEXORA wolf
companions use this identifier; ordinary world wolves keep their vanilla look.

## Build

Run `python3 resource-pack/build_pack.py`. It creates
`resource-pack/build/NEXORA_Beasts_RP.mcpack`.

## Server installation

The .mcpack is a Bedrock resource pack, not a plugin JAR. Install it through
the Nukkit-MOT resource-pack configuration and require clients to accept the
server pack. Keep the existing vanilla fallback until the custom entity has
been tested on the exact server/client versions.

This is a first prototype, not the completed set of all 13 species or rarity
skins. The remaining species, rarity effects, and animations are staged for
later iterations after the wolf prototype is validated.

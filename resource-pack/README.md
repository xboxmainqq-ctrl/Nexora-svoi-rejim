# NEXORA BEASTS — Bedrock Resource Pack

The pack currently contains the first isolated custom-entity prototype:
`nexora:beast_wolf`.

The wolf companion has its own geometry and generated pixel-art texture, plus
separate idle and walking animations. The Nukkit-MOT plugin registers the
matching network identifier and reuses the vanilla wolf server implementation
for movement and combat. Only NEXORA wolf companions use this identifier;
ordinary world wolves keep their vanilla appearance.

## Build

Run `python3 resource-pack/build_pack.py`. It creates
`resource-pack/build/NEXORA_Beasts_RP.mcpack`.

The GitHub Actions workflow publishes the plugin JAR and resource pack as
separate artifacts. The resource-pack artifact is a ZIP containing the
`.mcpack`; extract the ZIP first, then import or install the contained pack.

## Server installation

The `.mcpack` is a Bedrock resource pack, not a plugin JAR. Install it through
the Nukkit-MOT resource-pack configuration and require clients to accept the
server pack. Keep the vanilla fallback until the custom entity has been tested
on the exact server and client versions.

This is still a first prototype, not the completed set of all 13 species or
rarity-specific appearances/effects. The wolf's idle and walk animation files
are included, but their in-game playback and custom entity rendering still
need a live client test.

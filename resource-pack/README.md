# NEXORA BEASTS — Bedrock Resource Pack

This pack contains the NEXORA custom companion models, rarity visuals, particles,
and bitmap gradient lettering used by the server plugins.

## Privilege gradients

The NEXORA_Donate catalogue uses eight original project rank names:
AURVEX, ZYRETH, VELORIA, KRYON, NYMERIS, SOLVEX, ECLYRA and NEXORION.
Their labels use the pack's pre-rendered bitmap gradients (Ocean, Neon, Royal,
and Sunset), not Minecraft RGB formatting codes. NEXORA_Donate only displays
player privileges. Staff ranks and administrative tools are separate and are
not listed in /donate. Purchases remain disabled until the NEXORA website and
payment integration are ready.

## Build

Run `python3 resource-pack/build_pack.py`. It creates
`resource-pack/build/NEXORA_Beasts_RP.mcpack`.

GitHub Actions publishes plugin JARs and the resource pack as separate artifacts.
The resource-pack artifact is a ZIP containing the .mcpack; extract the ZIP first.

## Server installation

Install the .mcpack through the Nukkit-MOT resource-pack configuration and
require clients to accept the server pack. Keep a vanilla fallback until custom
content has been tested on the exact server and client versions.

## Rank plugin installation

Install these three server plugins together:
- `NEXORA_Survival` — gameplay, homes, kits, clans and chat.
- `NEXORA_Donate` — the eight donation privileges and `/donate` catalogue.
- `NEXORA_AdminRanks` — the six staff ranks and `/adminrank` management.

Survival requires both rank plugins. On first startup it migrates old rank assignments from `plugins/NEXORA_Survival/ranks.yml` into the new plugin data folders. The legacy file is retained as a backup; homes, kits, cooldowns and clan files are not reset.

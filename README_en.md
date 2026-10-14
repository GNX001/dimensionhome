# Dimension Home

> A void sky dimension that belongs to you.
> Build a gate out of **smooth stone**, light it with flint and steel, and step into a pure void sky island;
> when you walk back, you return to **the dimension you came from, next to the very gate you used**.

**中文: [README.md](README.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-DFA037?style=flat-square)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Release](https://img.shields.io/github/v/release/GNX001/dimensionhome?style=flat-square)](https://github.com/GNX001/dimensionhome/releases)

---

## What Is This

In any dimension (**Overworld / Nether / End**) you can build a smooth stone gate. Light it and you enter a pure void sky dimension called "Dimension Home: Sky Island". The island contains only **one landing platform as wide as the gate** and **one single** return gate — nothing else.

This is not a "resource dimension", but a **clean private space**: coordinates map 1:1, there is a day/night cycle, lighting works normally. It is good for building a base, doing redstone, isolated testing, or simply finding a place where nobody bothers you.

## Core Features

| Feature | Details |
| --- | --- |
| Coordinate scale | **1:1** — walk 100 blocks in the Overworld, walk 100 blocks on the island (**not** the Nether-style 8:1 scale) |
| Gate color | **Gold `#FFD700`** (identical in all three places: the gate blocks, the screen overlay while teleporting, and the particles at the gate) |
| Day/night | Has a day/night cycle, flowing in sync with the Overworld |
| Sky light | Yes (`has_skylight = true`) |
| Terrain | **Pure void** — no floating islands, ores, trees, structures, or villages are generated |
| Landing platform | **As wide as** the gate (including both side pillars), and extending **2 blocks** forward and backward along the gate's normal direction |
| Return logic | Returns to **the dimension you came from**, next to **the original gate** (Nether in → Nether out, End in → End out) |
| Frame block | `minecraft:smooth_stone`, lit with flint and steel |
| Mob spawning | Same as the Overworld (mobs spawn, but raids are **not** triggered) |
| Beds | Work (`bed_works = true`) |
| Respawn anchors | Do not work (`respawn_anchor_works = false`) |
| Entity passage | **Players, mobs, animals, and dropped items can all pass through the gate** |

## Sky Island Rules

The sky island is pure void. To keep the "only one gate" design while making sure you **never get trapped if the gate is destroyed**, the ignition rules on the island differ from the Overworld:

```
        Ignitable zone: horizontal +/-12, vertical y=99 .. 122

        +-------------------------------------------+
        |                                           |
        |        +-- the only return gate --+       |
        |        |       (2x3 default)     |       |
        |   =====+=========================+=====   |  <- y=99 landing platform
        |        (as wide as the gate,              |
        |         2 blocks forward and backward)    |
        |                                           |
        +-------------------------------------------+
                    horizontal +/-12
```

- **Inside the zone** you may light a frame of **any shape** at any time (width 2~21, height 3~21), but **only one gate may exist at a time**
- When a gate **already exists** in the zone, lighting a second one is **rejected**
- Lighting **outside the zone** is **rejected**
- The island's **first gate** is **automatically replicated** in the shape and orientation of "the source gate that the first visitor walked through"
- Therefore: **destroying the gate does not trap you** — just build another frame inside the zone and light it

> If you lose both your flint and steel AND your smooth stone into the void, you can run `/dimensionhome home` on the island to escape directly.

## Installation

### Requirements

| Item | Requirement |
| --- | --- |
| Minecraft | **1.20.1** |
| Forge | **47.x** (tested on 47.4.10 / 47.4.26) |
| Java | **17** |

### Required Dependency ⚠️

**[Custom Portal API Reforged 1.0.2](https://www.curseforge.com/minecraft/mc-mods/custom-portal-api-reforged)**
(mod ID: `cpapireforged`, file name `customportalapi-reforged-1.0.2.jar`)

> **This is a required dependency, not an optional one.** Without it the game crashes on startup and reports in the log:
> `Missing or unsupported mandatory dependencies: Mod ID: 'cpapireforged'`

### Installation Steps

1. Download `dimensionhome-1.0.0.jar` from [Releases](https://github.com/GNX001/dimensionhome/releases)
2. Put **both jars together** into the game's `mods\` folder:
   - `dimensionhome-1.0.0.jar`
   - `customportalapi-reforged-1.0.2.jar`
3. Fully restart the game (returning to the main menu is not enough)
4. The "Mods" list should show both **Dimension Home** and **Custom Portal API ReForged**

The same applies to a multiplayer server: both jars go into the server's `mods\` folder.

## How To Play

1. Mine stone in the Overworld and smelt it into **smooth stone** (`smooth_stone`), then build a frame with an interior of at least **2 wide × 3 high**
2. Hold **flint and steel** and right-click the inside bottom of the opening → the opening fills with a layer of **gold**
3. Stand inside for about **4 seconds** (about 1 second in Creative mode) → you arrive on the sky island
4. The island has a single return gate; walk into it to go back to where you came from

## Built-in Self-Test Commands

This mod ships with two self-test commands, so every rule can be checked **reproducibly** instead of by eye:

```
/dimensionhome selftest    # dimension parameters / void terrain / landmark geometry / ignition rules / registration state (40 checks)
/dimensionhome tptest      # the whole teleport chain: one full round trip each for a player and a dropped item (26 checks)
/dimensionhome home        # fallback escape (only effective inside the sky dimension)
```

Requires OP or single-player cheats.

## Building From Source

```bat
git clone https://github.com/GNX001/dimensionhome.git
cd dimensionhome
set JAVA_HOME=<path to your JDK 17>
gradlew.bat build
```

Output: `build\libs\dimensionhome-1.0.0.jar`

> **Do not change `org.gradle.daemon=false` to `true` in `gradle.properties`.**
> In daemon mode ForgeGradle fails with
> `Project services has been closed` and the build breaks.

To run the development environment directly:

```bat
gradlew.bat runClient    :: launch a client with the mod loaded
gradlew.bat runServer    :: launch a server with the mod loaded
```

## Project Structure

```
dimensionhome/
├─ README.md                        Chinese readme
├─ README_en.md                     English readme (this file)
├─ LICENSE                          MIT
├─ publish.ps1                      helper script to commit and push
├─ src/main/java/com/dimensionhome/
│  ├─ DHConfig.java                 global constants (single source of truth)
│  ├─ DimensionHome.java            main class (mod entry + portal registration)
│  ├─ DHModEvents.java              mod event bus (capability registration, portal registration)
│  ├─ DHEvents.java                 game event bus (capability attach / clone / tick / server start)
│  ├─ DHSelfTest.java               selftest | tptest | home commands
│  ├─ capability/
│  │  └─ PortalOriginCapability.java  records origin dimension and gate position (persists after player death)
│  ├─ portal/
│  │  └─ DHTeleportHandler.java     teleport takeover: landing control, origin recording, non-player entity path
│  └─ world/
│     ├─ VoidChunkGenerator.java    pure void chunk generator
│     ├─ SkyGateShape.java          gate shape / footprint / platform and landing geometry derivation
│     ├─ SkyGateGeometry.java       axis conversion, zone checks, gate scanning inside the zone
│     ├─ SkyGateData.java           SavedData for the first gate shape (written only once)
│     ├─ SkyGuardPortalFrameTester.java  allow inside the zone / block ignition outside / single gate
│     └─ SkyLandmarkBuilder.java    landing platform + anchor gate build / erase / measure
├─ src/main/resources/
│  ├─ META-INF/mods.toml            mod metadata and dependency declaration
│  ├─ README.txt                    Chinese readme bundled inside the jar
│  ├─ assets/dimensionhome/lang/    Chinese and English language files
│  └─ data/dimensionhome/           dimension type + LevelStem datapack
└─ docs/
   ├─ 使用说明.md                    full Chinese user guide (install / gameplay / acceptance / FAQ)
   ├─ RELEASE_NOTES_v1.0.0.md       v1.0.0 release notes
   └─ 开发文档.txt                    the original development document
```

## Technical Notes

A few pitfalls worth recording, for anyone writing a similar mod:

- **Blocking ignition on the island** cannot be done by intercepting a Forge event — flint-and-steel ignition goes through CPA's own `AbstractFireMixin` (which injects directly into `BaseFireBlock.onPlace`), so cancelling the event has no effect. Instead we use a **custom `PortalFrameTester`** that returns `Optional.empty()` from `getNewPortal` — that is the only effective hook.
- **Returning to the original gate** cannot be expressed with CPA's `returnDimID` (it only supports one fixed return dimension). So we take over teleportation completely by returning `CANCEL_TP` from CPA's `registerBeforeTPEvent`, record the origin dimension and coordinates ourselves, and then perform a precise landing.
- **Non-player entities** cannot use `ServerPlayer.teleportTo`; they need `changeDimension(ServerLevel, ITeleporter)`. And a custom `ITeleporter` **must override `getPortalInfo`** — Forge's default implementation only uses the vanilla algorithm when `isVanilla()` is true; a self-written teleporter is always `false`, so it would treat the entity's **current position** as the destination and the entity would not move at all.
- **The cooldown must be set before `changeDimension`**, because `Entity.restoreFrom` copies `portalCooldown` onto the new entity instance.

## Verified

Verified on a **real Forge 1.20.1 production server** (installed with the official installer, not a development environment):

- ✅ Server starts normally, mod loads without errors, the landmark is built automatically
- ✅ `/dimensionhome selftest` — **40/40 checks passed**
- ✅ `/dimensionhome tptest` — **26/26 checks passed** (one full round trip each for a player and a dropped item)
- ✅ Lighting a smooth stone gate in the Overworld → a portal is created correctly
- ✅ Lighting **outside** the island zone → correctly rejected
- ✅ Lighting **inside the zone while a gate already exists** → correctly rejected (the single-gate rule holds)
- ✅ Lighting **inside the zone after the gate was destroyed** → **a gate is created successfully** (the "destroyed gate traps you" bug is fixed)
- ✅ First gate shape replication: the source gate shape is measured, recorded only once, and rebuilding from the shape produces a complete frame with no overlap between old and new
- ✅ Landing platform laid out from the gate shape: as wide as the gate (including both side pillars), extending 2 blocks forward and backward along the gate's normal
- ✅ Landing position computed dynamically: a default 2-wide gate → `(0.5, 100.0, 0.5)`
- ✅ The portal is tinted gold `#FFD700` (confirmed by an assertion that reads back `link.colorID` at runtime)
- ✅ **Non-player entities** (dropped items / mobs / animals) travel through the gate in both directions, their origin survives in NBT, and they return to their original position

**Version compatibility**: fully verified separately on **Forge 47.4.10** and **47.4.26**.

## License

[MIT](LICENSE) © 2026 GNX001

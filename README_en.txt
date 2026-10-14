================================================================================
  Dimension Home  --  English README
================================================================================

  A private, empty sky dimension that belongs to you alone.

  Build a frame out of smooth stone, light it with flint and steel, and step
  into an empty sky. Walk back through the return gate and you arrive in the
  dimension you originally came from -- right next to the very portal you used.

--------------------------------------------------------------------------------
  Minecraft 1.20.1  |  Forge 47.x  |  Java 17  |  MIT License
  Repository: https://github.com/GNX001/dimensionhome
  This file is the English translation of README.md (Chinese).
--------------------------------------------------------------------------------


================================================================================
  1. WHAT IS THIS
================================================================================

  You can build a smooth stone frame in ANY dimension -- Overworld, Nether, or
  End. Light it and you travel to a pure void sky dimension called
  "Dimension Home: Sky Island".

  The island contains exactly one landing platform (as wide as your gate) and
  exactly one return gate. Nothing else exists there.

  This is not a "resource dimension". It is a clean private space:

    * Coordinates map 1:1 (no Nether-style compression)
    * Day and night cycle, normal sky light
    * Good for building a base, redstone work, isolated testing, or simply
      having a place where nobody bothers you


================================================================================
  2. CORE FEATURES
================================================================================

  Feature              Details
  -------------------- -------------------------------------------------------
  Coordinate scale     1:1 -- walk 100 blocks in the Overworld, walk the same
                       100 blocks on the island (NOT the Nether's 8:1)
  Portal color         Gold #FFD700 -- applied consistently to the portal
                       blocks, the screen overlay while teleporting, and the
                       particles around the gate
  Day/night cycle      Yes, normal daylight cycle
  Sky light            Yes (has_skylight = true)
  Terrain              Pure void -- no floating islands, ores, trees,
                       structures, or villages are generated
  Landing platform     As wide as the gate (including both side pillars), and
                       extending 2 blocks forward and backward along the gate's
                       normal direction
  Return logic         Returns to the dimension you came from, next to the
                       original gate (Nether in -> Nether out, End in -> End out)
  Frame block          minecraft:smooth_stone, lit with flint and steel
  Mob spawning         Same as the Overworld (mobs spawn, but raids do NOT
                       trigger)
  Beds                 Work (bed_works = true)
  Respawn anchors      Do NOT work (respawn_anchor_works = false)
  Entity passage       Players, mobs, animals, and dropped items can ALL pass
                       through the gate


================================================================================
  3. HOW THE SKY ISLAND WORKS  (important)
================================================================================

  The sky island is pure void. To keep the "only one gate" design while making
  sure you can NEVER get trapped if the gate is destroyed, the ignition rules
  on the island differ from the Overworld:

    * The island has a designated IGNITABLE ZONE: horizontal +/-12 blocks,
      vertical y=99 to y=122

    * INSIDE the zone you may light a frame of ANY shape at any time
      (width 2..21, height 3..21) -- but only ONE gate may exist at a time

    * If a gate ALREADY EXISTS in the zone, lighting a second one is REJECTED

    * Lighting a frame OUTSIDE the zone is REJECTED

    * The island's FIRST gate is automatically REPLICATED from the shape and
      orientation of the source gate that the first visitor walked through

    * Therefore: destroying the gate does NOT trap you. Just build a new frame
      inside the zone and light it.

  ASCII layout of the zone:

                  Ignitable zone (horizontal +/-12, vertical y=99..122)

        +-------------------------------------------------+
        |                                                 |
        |         +-- the only return gate --+            |
        |         |     (2x3 by default)     |            |
        |   ======+==========================+=======     |  y=99 platform
        |         as wide as the gate, extending          |
        |         2 blocks forward and backward           |
        |                                                 |
        +-------------------------------------------------+
                      horizontal +/-12 blocks

  FALLBACK: If you somehow lose both your flint and steel AND your smooth stone
  into the void, run "/dimensionhome home" while on the island to escape
  directly.


================================================================================
  4. REQUIRED DEPENDENCY  (NOT OPTIONAL)
================================================================================

  Custom Portal API Reforged 1.0.2
  Mod ID: cpapireforged
  File name: customportalapi-reforged-1.0.2.jar
  Download: https://www.curseforge.com/minecraft/mc-mods/custom-portal-api-reforged

  WARNING: This is a MANDATORY dependency, not an optional one. Without it the
  game crashes on startup with:

      Missing or unsupported mandatory dependencies:
      Mod ID: 'cpapireforged', Requested by: 'dimensionhome'

  Your mods folder must contain BOTH jars:

      mods\
      |-- dimensionhome-1.0.0.jar              <- this mod
      +-- customportalapi-reforged-1.0.2.jar   <- required dependency (107,408 bytes)


================================================================================
  5. REQUIREMENTS
================================================================================

  Minecraft            1.20.1
  Forge                47.x  (tested on 47.4.10 and 47.4.26)
  Java                 17


================================================================================
  6. INSTALLATION
================================================================================

  Single player:

    1. Download dimensionhome-1.0.0.jar from the Releases page:
       https://github.com/GNX001/dimensionhome/releases

    2. Download customportalapi-reforged-1.0.2.jar from CurseForge (section 4).

    3. Put BOTH jars into your mods folder.
       Typically .minecraft\mods\ -- or, if your launcher uses version
       isolation, .minecraft\versions\<version>\mods\

    4. Fully restart the game (returning to the main menu is not enough).

    5. The "Mods" list should show BOTH "Dimension Home" and
       "Custom Portal API ReForged".

  Multiplayer server:

    Put both jars into the server's mods\ folder. Same rules.


================================================================================
  7. HOW TO PLAY
================================================================================

    1. Mine stone and smelt it into SMOOTH STONE (minecraft:smooth_stone).
       Build a frame with an interior of at least 2 blocks wide x 3 blocks high.

    2. Hold FLINT AND STEEL and right-click the inside bottom of the opening.
       The opening fills with a layer of GOLD.

    3. Stand inside for about 4 seconds (about 1 second in Creative mode).

    4. You arrive on the sky island. Walk into the return gate to go back to
       where you came from.


================================================================================
  8. BUILT-IN SELF-TEST COMMANDS
================================================================================

  This mod ships with self-test commands so every rule can be checked
  reproducibly instead of by eye:

      /dimensionhome selftest    Dimension params, void terrain, landmark
                                 geometry, ignition rules, registration (40 checks)

      /dimensionhome tptest      Full teleport chain: one complete round trip
                                 for a player, and one for a dropped item
                                 (26 checks)

      /dimensionhome home        Fallback escape (only works inside the
                                 sky dimension)

  These require OP or single-player cheats.


================================================================================
  9. BUILDING FROM SOURCE
================================================================================

      git clone https://github.com/GNX001/dimensionhome.git
      cd dimensionhome
      set JAVA_HOME=<path to your JDK 17>
      gradlew.bat build

  Output: build\libs\dimensionhome-1.0.0.jar

  IMPORTANT: Do NOT change "org.gradle.daemon=false" to true in
  gradle.properties. In daemon mode ForgeGradle fails with
  "Project services has been closed" because the toolchain service is closed
  early, and the build breaks.

  To run the development client/server directly:

      gradlew.bat runClient    Start a client with the mod loaded
      gradlew.bat runServer    Start a server with the mod loaded


================================================================================
 10. PROJECT STRUCTURE
================================================================================

      dimensionhome/
      |-- src/main/java/com/dimensionhome/
      |   |-- DHConfig.java                 Global constants (single source of truth)
      |   |-- DimensionHome.java            Main class (mod entry + portal registration)
      |   |-- DHModEvents.java              Mod event bus (capability, portal registration)
      |   |-- DHEvents.java                 Game event bus (attach/clone/tick/server start)
      |   |-- DHSelfTest.java               selftest | tptest | home commands
      |   |-- capability/
      |   |   +-- PortalOriginCapability.java   Records origin dimension and gate
      |   |                                     position; survives player death
      |   |-- portal/
      |   |   +-- DHTeleportHandler.java    Teleport takeover: landing control,
      |   |                                 origin recording, non-player path
      |   +-- world/
      |       |-- VoidChunkGenerator.java    Pure void chunk generator
      |       |-- SkyGateShape.java          Gate shape / footprint / platform and
      |       |                              landing geometry derivation
      |       |-- SkyGateGeometry.java       Axis conversion, zone checks, gate scan
      |       |-- SkyGateData.java           SavedData for the first gate shape
      |       |                              (written once)
      |       |-- SkyGuardPortalFrameTester.java  Allow inside zone / deny outside /
      |       |                                   enforce single gate
      |       +-- SkyLandmarkBuilder.java    Landing platform + anchor gate build,
      |                                      erase, measure
      |-- src/main/resources/
      |   |-- META-INF/mods.toml            Mod metadata and dependency declaration
      |   |-- README.txt                    Chinese readme bundled inside the jar
      |   |-- assets/dimensionhome/lang/    Chinese and English language files
      |   +-- data/dimensionhome/           Dimension type + LevelStem datapack
      +-- docs/
          |-- 使用说明.md                    Full Chinese user guide
          |-- RELEASE_NOTES_v1.0.0.md       Release notes
          +-- 开发文档.txt                    Original requirements document


================================================================================
 11. TECHNICAL NOTES
================================================================================

  A few implementation pitfalls worth recording, for anyone writing a similar
  mod:

  * You CANNOT block ignition inside the sky island with a Forge event.
    Flint-and-steel ignition goes through CPA's own AbstractFireMixin, which
    injects directly into BaseFireBlock.onPlace -- cancelling the Forge event
    has no effect. The only effective hook is a custom PortalFrameTester that
    returns Optional.empty() from getNewPortal.

  * "Return to the original gate" cannot be expressed with CPA's returnDimID
    (it only supports one fixed return dimension). Instead we take over
    teleportation entirely by returning CANCEL_TP from CPA's
    registerBeforeTPEvent, record the origin dimension and coordinates
    ourselves, and then perform a precise landing.

  * Non-player entities cannot use ServerPlayer.teleportTo. They need
    changeDimension(ServerLevel, ITeleporter) -- and a custom ITeleporter MUST
    override getPortalInfo. Forge's default implementation only uses the
    vanilla algorithm when isVanilla() is true; a custom teleporter is always
    false, so the default would use the entity's CURRENT position as the
    destination and the entity would never actually move.

  * The portal cooldown must be set BEFORE calling changeDimension, because
    Entity.restoreFrom copies portalCooldown onto the new entity instance.

  * A cross-dimension teleport can create a NEW entity instance. Newly added
    entities may sit in the server's pending queue until a later tick if the
    target chunk is not entity-ticking yet, so "teleport then immediately scan
    the dimension for the entity" is inherently unreliable. The teleport method
    therefore returns the resulting entity directly.


================================================================================
 12. VERIFIED
================================================================================

  Verified on a REAL Forge 1.20.1 production server (installed with the
  official installer, not a development environment):

    [OK]  Server starts normally, mod loads without errors, landmark is
          built automatically
    [OK]  /dimensionhome selftest  --  40/40 checks passed
    [OK]  /dimensionhome tptest    --  26/26 checks passed
          (run 8 times consecutively, all passed)
    [OK]  Lighting a smooth stone gate in the Overworld creates a portal
    [OK]  Lighting OUTSIDE the island zone is correctly rejected
    [OK]  Lighting INSIDE the zone when a gate already exists is correctly
          rejected (single-gate rule holds)
    [OK]  Lighting INSIDE the zone AFTER the gate was destroyed SUCCEEDS
          (the "destroyed gate traps you" bug is fixed)
    [OK]  First-gate shape replication: the source gate shape is measured,
          recorded only once, and rebuilding produces a complete frame with
          no overlap between old and new
    [OK]  Landing platform follows the gate shape: as wide as the gate
          (including both side pillars), extending 2 blocks forward and back
    [OK]  Landing position is computed dynamically: a default 2-wide gate
          lands you at (0.5, 100.0, 0.5)
    [OK]  The portal is colored gold #FFD700 (confirmed by reading back
          link.colorID at runtime)
    [OK]  NON-PLAYER entities (dropped items, mobs, animals) travel with the
          gate, their origin survives in NBT, and they return to their
          original position
    [OK]  Self-test commands do not leave force-loaded chunks behind in the
          save file

  Version compatibility: fully verified on BOTH Forge 47.4.10 and 47.4.26.


================================================================================
 13. UPGRADING FROM AN EARLIER VERSION
================================================================================

  Early versions used a hard-coded 3x3 landing platform. After upgrading to
  this version, any leftover smooth stone behind the gate that falls outside
  the new platform area is cleaned up automatically when the server starts.

  The cleanup only removes blocks that are CURRENTLY smooth stone, so anything
  you placed yourself is left untouched.


================================================================================
 14. LICENSE
================================================================================

  MIT License. See the LICENSE file in the repository root.

  Copyright (c) 2026 GNX001


================================================================================
  LINKS
================================================================================

  Repository     https://github.com/GNX001/dimensionhome
  Releases       https://github.com/GNX001/dimensionhome/releases
  Issues         https://github.com/GNX001/dimensionhome/issues
  Chinese guide  docs/使用说明.md
  Dependency     https://www.curseforge.com/minecraft/mc-mods/custom-portal-api-reforged

================================================================================

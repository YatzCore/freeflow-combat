# Freeflow Combat

Rhythm-based freeflow melee for Minecraft (Fabric 1.21.1), inspired by the Arkham games:
fists only, target snapping, combos, counters, rolls and vaults, stuns, beatdowns, takedowns,
turn-taking enemies, an over-the-shoulder combat camera and PS5 DualSense support.
All moves, animations and names are original.

## Install

1. Minecraft **1.21.1** with **Fabric Loader** 0.16 or newer.
2. Put in your `mods` folder:
   - `freeflow-combat-<version>.jar` (playerAnimator and Cloth Config are bundled inside)
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1
   - optional: [Mod Menu](https://modrinth.com/mod/modmenu), for a settings button in the mod list
3. Multiplayer: install it on the server **and** every client. The server's gameplay settings
   apply to everyone; camera, effects and controller settings stay each player's own.

## How it works

Freeflow turns on by itself when hostile mobs come for you and your main hand is empty, and
ends a few seconds after the fight. Outside fights the game plays exactly as normal.
Enemies take turns: a **blue !** means you can counter, a **red !** means you can't (armed
enemies, shooters): roll away.

## Controls

| Action | Keyboard / mouse | DualSense (Arkham Knight layout) |
|---|---|---|
| Strike (toward the enemy you aim or move at) | Left click | □ |
| Counter (one press per warning) | Right click | △ |
| Stun | G | ○ |
| Beatdown | Strike a stunned enemy repeatedly | ○ then □ □ □ |
| Run | Hold Space | Hold ✕ |
| Roll / vault over an enemy | Double-tap Space or a direction, or Left Alt | Double-tap ✕ |
| Slide (knocks enemies down) | Tap Ctrl while running | Tap R2 while running |
| Ground takedown (enemy on the floor) | V | R2 + △ |
| Special takedown (combo x8) | Z | ○ + △ |
| Swap camera shoulder | H | R3 |
| Combat camera on/off | J | D-pad → |
| Cycle combat mode (auto / on / off) | B | D-pad ↑ |
| Settings | K | Create |
| Debug overlay | F7 | |

All keys can be rebound in Options > Controls. Outside fights the controller plays Minecraft:
✕ jump, L3 sprint, ○ sneak, R2/□ attack, L2 use, △ inventory, L1/R1 hotbar, D-pad ↓ drop.

## Settings

Press **K** (or use Mod Menu). Every timing, range, damage value, enemy role list, camera
option and controller option is there, saved to `config/freeflow.json`. On a server,
operators can edit that file and run `/freeflow reload`.

## Building

```
./gradlew build          # jar in build/libs
./gradlew runGametest    # headless in-game tests
./gradlew runClient      # dev client
./gradlew prodServer     # the built jar on a production Fabric server (run-prod/)
```

Animations are generated with `python tools/gen_animations.py`; see [docs/CREDITS.md](docs/CREDITS.md).

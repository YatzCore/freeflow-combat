# Freeflow Combat Mod — Full Plan (fists only)

## Context
Bring Arkham-style **freeflow combat** (rhythm combos, target snapping, counters, evades, takedowns, turn-taking enemies) into Minecraft as an original mod. The user wants the **full feature list**, **fists only** (no weapon movesets), with a **gadget system to be added later** — so the architecture leaves a clean extension point for gadgets now, without building any.

Mechanics are inspired by the genre; all names, sounds, textures and animations are original (no Batman/WB branding or assets).

Project lives in this repo; JDK 21 (Temurin) is required to build.

## Tech stack
- **Fabric**, Minecraft **1.21.1**, Java 21, Gradle (Fabric example-mod template, Loom).
- Deps: Fabric API, **playerAnimator** (player keyframe animations), **Cloth Config** + **ModMenu** (settings). GeckoLib not needed (we animate the player, not custom mobs).
- Mod id: `freeflow` (working title, e.g. "Freeflow Combat"). Package `com.borislav.freeflow` (or the user's preferred name).
- **Version control:** `git init`, Java/Gradle `.gitignore` (build/, .gradle/, run/, out/, .idea/), first commit, then create a **private** GitHub repo with `gh repo create freeflow-combat --private --source . --push` (gh CLI is installed and logged in as `YatzCore`). Commit + push at the end of each milestone.

## Feature list (all in scope)
1. **Combat mode** — auto-enters when hostiles within ~12 blocks target the player; exits after ~4s calm. Manual toggle key. Outside combat mode, vanilla behavior is untouched (mining/building safe). Fists only: freeflow only activates with an empty main hand (config: allow any non-weapon item).
2. **Strike + target snap** — left-click picks best target in a ~60° cone within 8 blocks (score = angle, distance, threat, input direction via WASD). Player lunges (scripted velocity, brief i-frames) and hits. Alternating punch/kick animations.
3. **Combo meter** — +1 per hit; breaks after ~1.5s idle or when damaged. Combo scales damage, lunge range (up to ~12 blocks at high combo), and animation speed.
4. **Rhythm** — clicking faster than the beat window = "whiff" stagger (~0.4s); on-beat keeps flow. HUD pulse shows the window.
5. **Counter** — mobs telegraph attacks with a floating marker ~0.5s ahead (combat director delays their attack). Counter on **right mouse** (or `R`) in window: cancel attack, counter animation, knockdown/stun. **One press per warning**: when several enemies telegraph at once you must counter each one (soonest first); any you miss hits you.
6. **Evade / vault** — double-tap direction or `Space`+dir in combat: dodge roll. Toward an enemy: vault over it (lands behind, keeps combo).
7. **Stun + beatdown** — stun key (`G`) dazes target; holding strike on a stunned mob = rapid beatdown flurry ending in knockback.
8. **Ground takedown** — knocked-down mobs (beatdown finisher) can be finished with the ground takedown key (V): takes 1s, player locked and vulnerable (getting hit interrupts it); knocks out non-boss mobs.
9. **Special takedown** — at combo ×8, special key (`F`) instantly KOs a non-boss mob; combo spends 8.
10. **Enemy roles** (via combat director tags, no new mob entities needed):
    - Normal (zombie, husk, drowned, piglin…): counterable.
    - **Shielded** (mobs holding a shield): strikes bounce — must stun or vault-over first.
    - **Armed / uncounterable** (red telegraph, e.g. vindicator, mobs with axes): must evade.
    - **Ranged** (skeleton, pillager): telegraph projectile; counter = deflect/dodge, director limits active shooters.
    - Bosses/large mobs (warden, ravager, iron golem): immune to takedowns, reduced snap.
11. **Combat director** — per-player attack-token system: max **4** mobs attacking at once (3-tick gap between starts), others circle/strafe at distance. This is the core "Arkham feel" piece.
12. **Juice** — FOV punch on hit, hit-stop (2–3 frame freeze), optional slow-mo + camera nudge on the last enemy's final hit, custom sounds, impact particles, combo HUD with flair at x5/x10/x20.
13. **Arkham-style camera** (client-only, toggleable):
    - **Over-the-shoulder 3rd person** as the default in combat mode (optional: everywhere). Offset right of the player, smooth spring-damped follow; shoulder swap key.
    - **Decoupled look/move:** mouse orbits the camera; WASD moves the player **relative to the camera** and the body turns to face the movement direction (not the mouse). Strike direction = WASD input relative to camera (feeds TargetSelector), like the original games.
    - **Dynamic combat framing:** camera pulls back and raises (distance ~4 → ~7 blocks, slight FOV widen) as more enemies engage; it softly biases toward the group's centroid so all attackers stay on screen; returns to normal when combat ends.
    - **Collision:** raycast from player to desired camera position, pull in against walls without clipping; fade the player model when the camera is very close.
    - **Cinematic moments:** brief camera swing to a side angle + slow-mo on special takedowns and the final hit of a fight (config toggle; disabled automatically in multiplayer slow-mo, camera-only).
    - **Crosshair:** hidden in combat (targeting is automatic); small target indicator on the currently snapped enemy instead.
14. **Scoring/XP bonus** — end-of-fight popup: max combo, counters, variety bonus; awards small vanilla XP.
15. **Config** — all timing windows, damage scaling, ranges, mob role lists (by entity tag), camera distances/offset/smoothing, toggles for slow-mo/hit-stop/HUD/camera.
16. **PS5 DualSense controller support** (client):
    - Read the pad through LWJGL/GLFW's gamepad API (`glfwGetGamepadState`, SDL mappings; DualSense works over USB and Bluetooth on Windows). No extra native library needed.
    - **Left stick** = camera-relative movement (feeds `RelativeMovement`, analog speed); **right stick** = orbit camera (deadzone + response curve + sensitivity).
    - Combat layout (rebindable, Arkham-like): **□ Square** strike, **△ Triangle** counter, **○ Circle** stun, **✕ Cross** evade/vault (double-tap direction also works), **R2** ground takedown, **□+△** special takedown, **L1** swap shoulder, **R3** toggle camera, **Touchpad** cycle combat mode, **Options** opens the pause menu.
    - Out of combat the pad still walks, looks, jumps (✕), attacks/mines (R2) and uses items (L2), with hotbar on L1/R1 outside combat. Inventory/menu cursor control is left to **Controlify** if the player installs it: detect it and only add our combat bindings on top of it, so they don't conflict.
    - Button-prompt glyphs (□ △ ○ ✕) in the combat HUD when a pad is the last-used input.
    - Stretch: DualSense **rumble/haptics and adaptive triggers** on hits/counters via HID output reports (needs a HID library such as hid4java); optional and behind a config flag.
17. **Multiplayer-safe** — server-authoritative; each player has their own director tokens.

## Architecture
```
src/main/java/com/borislav/freeflow/
  FreeflowMod.java                 // common init: registries, packets, events
  combat/
    CombatState.java               // per-player: combo, lastHitTick, target, cooldowns, mode
    CombatStateManager.java        // map UUID -> CombatState (server), tick update
    CombatModeTracker.java         // enter/exit combat mode
    TargetSelector.java            // cone + scoring
    moves/Move.java                // interface: canExecute(), execute(), animationId
    moves/StrikeMove, CounterMove, EvadeMove, VaultMove, StunMove,
          BeatdownMove, GroundTakedownMove, SpecialTakedownMove
    MoveRegistry.java              // registry of moves -> gadgets later register here too
  ai/
    CombatDirector.java            // attack tokens, telegraph scheduling
    EnemyRole.java                 // NORMAL, SHIELDED, ARMED, RANGED, BOSS (resolved from tags)
    goal/CircleTargetGoal.java     // strafe at distance while waiting for token
    goal/TelegraphedAttackGoal.java
  gadget/
    Gadget.java                    // EMPTY extension point: interface only (use, cooldown, quick-fire in combat)
  network/                         // C2S: StrikePacket, CounterPacket, EvadePacket, StunPacket, TakedownPacket
                                   // S2C: ComboSyncPacket, TelegraphPacket, PlayAnimPacket, HitStopPacket
  mixin/
    MobEntityMixin                 // hook goal selection / attack timing for director
    MeleeAttackGoalMixin           // gate attacks on director token + telegraph delay
    LivingEntityMixin              // i-frames during moves, cancel damage on counter
  config/FreeflowConfig.java
src/client/java/com/borislav/freeflow/client/
  FreeflowClient.java, Keybinds.java
  ClientInputHandler.java          // intercept left-click in combat mode, send packets
  MovePrediction.java              // client-side lunge prediction (smooth feel)
  hud/ComboHud.java, RhythmPulse.java, TelegraphRenderer.java
  fx/HitStop.java, CameraFx.java (FOV punch, slow-mo)
  camera/FreeflowCamera.java       // orbit yaw/pitch, spring follow, shoulder offset, framing
  camera/CombatFraming.java        // distance/height/FOV from enemy count + centroid bias
  camera/CameraCollision.java      // raycast pull-in
  camera/CinematicShots.java       // takedown/finisher swing shots
  camera/RelativeMovement.java     // camera-relative WASD -> player yaw
  mixin/client/CameraMixin         // override Camera.update position/rotation
  mixin/client/MouseMixin          // route mouse delta to orbit camera instead of player
  mixin/client/KeyboardInputMixin  // remap WASD to camera-relative
  anim/AnimationController.java    // playerAnimator layers
src/main/resources/
  fabric.mod.json, freeflow.mixins.json, freeflow.client.mixins.json
  assets/freeflow/player_animation/*.json   // Blockbench-made animations
  assets/freeflow/sounds/, textures/gui/, lang/en_us.json
  data/freeflow/tags/entity_type/{shielded,armed,ranged,boss}.json
```

**Gadget-ready:** `Move` + `MoveRegistry` + `Gadget` interface let gadgets later plug in as quick-fire moves (e.g. a thrown stun disc, grapple pull) that cost combo or cooldown, without touching the core loop.

## Milestones (build order)
1. **Setup** — JDK 21, Fabric template, deps, git + private GitHub repo, runs in dev client.
2. **Combat mode + input** — keybinds, left-click interception, networking skeleton, debug overlay.
3. **Arkham camera (base)** — over-shoulder orbit, spring follow, collision, camera-relative movement. Built early since strike direction depends on it.
4. **Strike + snap + lunge** — TargetSelector (using camera-relative input), client-predicted lunge, server validation. Tune feel here.
5. **Combo + rhythm + HUD.**
6. **Combat director + telegraphs + counter** (incl. multi-counter).
7. **Evade / vault.**
8. **Stun, beatdown, ground takedown, special takedown.**
9. **Enemy roles** (shield/armed/ranged/boss via tags).
10. **Camera polish** — dynamic combat framing, cinematic takedown/finisher shots, crosshair → target indicator.
11. **Animations** — Blockbench → playerAnimator (placeholder vanilla swings until here).
12. **Juice** — hit-stop, FOV, slow-mo finisher, sounds, particles, end-of-fight score.
13. **Config + ModMenu screen.**
14. **DualSense controller** — GLFW gamepad input layer, stick movement/camera, combat button layout, Controlify detection, HUD glyphs; optional rumble/adaptive triggers last.
15. **Multiplayer test + balance pass**, then build release jar (GitHub release on the private repo).

## Risks
- **Feel/latency:** lunge must be client-predicted with server reconciliation, or it feels laggy on servers.
- **Vanilla AI:** each melee attack goal differs (zombie, piglin, vindicator…); mixins target `MeleeAttackGoal` broadly, special cases added per mob. Modded mobs: basic support only.
- **Animations** are the biggest art workload.
- **Camera mixins** can conflict with other camera mods (Shoulder Surfing Reloaded, Better Third Person) — detect them and disable ours, and keep camera fully toggleable.
- **Hit-stop/slow-mo** in multiplayer: client-only visual (no real tick slowdown on servers).

## Verification
- `./gradlew runClient` each milestone; test world with a superflat + spawned mob groups (commands: `/summon zombie` x5, mixed groups with shield/vindicator/skeleton).
- Checklist per milestone: snap picks the expected target; combo increments/breaks correctly; counter window works at default and edge timings; director never allows >2 simultaneous attackers; vanilla combat unaffected outside combat mode / when holding an item.
- Camera checks: orbit smooth with no jitter; no clipping into walls in 1-wide tunnels; WASD moves relative to camera; framing keeps all attackers on screen in a 6-mob fight; F5/vanilla perspectives restored when camera toggled off.
- GitHub: `gh repo view --json visibility` reports `PRIVATE`; each milestone pushed.
- Automated: `./gradlew runGametest` (headless GameTests) must pass every milestone.
- Controller: with a DualSense on USB and Bluetooth, sticks move/orbit with no drift at rest, every combat button triggers its move, keyboard/mouse still work at the same time, and no double-binding when Controlify is installed.
- `./gradlew runServer` + two clients for multiplayer check.
- `./gradlew build` produces a jar that loads in a normal Fabric 1.21.1 install with deps.

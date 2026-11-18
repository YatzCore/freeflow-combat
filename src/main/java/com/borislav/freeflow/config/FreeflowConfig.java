package com.borislav.freeflow.config;

import java.util.ArrayList;
import java.util.List;

import static com.borislav.freeflow.config.Setting.Category.CAMERA;
import static com.borislav.freeflow.config.Setting.Category.CONTROLLER;
import static com.borislav.freeflow.config.Setting.Category.DEFENCE;
import static com.borislav.freeflow.config.Setting.Category.EFFECTS;
import static com.borislav.freeflow.config.Setting.Category.ENEMIES;
import static com.borislav.freeflow.config.Setting.Category.GENERAL;
import static com.borislav.freeflow.config.Setting.Category.STRIKES;
import static com.borislav.freeflow.config.Setting.Category.TAKEDOWNS;

/**
 * Tunable values. Fields marked {@link Setting} are saved to {@code config/freeflow.json} and edited
 * on the settings screen (Mods menu); {@link ConfigStore} does the loading, saving and server sync.
 * The values here are the defaults.
 */
public final class FreeflowConfig {
	private FreeflowConfig() {}

	// --- General ---

	@Setting(category = GENERAL, name = "Combat engage radius", min = 4, max = 48,
			tip = "Blocks: a mob targeting you this close starts a fight.")
	public static double combatEngageRadius = 12.0;

	@Setting(category = GENERAL, name = "Calm time to end a fight", min = 10, max = 400,
			tip = "Ticks (20 = 1s) with no enemy engaged before combat mode ends.")
	public static int combatCalmTicks = 80;

	@Setting(category = GENERAL, name = "Fight rating XP", tip = "Award bonus XP for the fight rating when a fight ends.")
	public static boolean fightXp = true;
	@Setting(category = GENERAL, name = "Max XP per fight", min = 0, max = 1000,
			tip = "Caps the fight-rating bonus (stops long fights against big mobs from farming XP on servers).")
	public static int fightXpCap = 30;

	@Setting(category = GENERAL, name = "Arena for everyone",
			tip = "Let every player use /freeflow arena on a server (otherwise operators only; "
					+ "in singleplayer the world's owner can always use it).")
	public static boolean arenaForEveryone = false;

	/** How often (ticks) the server rescans for engaged mobs. */
	public static int combatScanInterval = 4;

	@Setting(category = GENERAL, name = "Allow items in hand",
			tip = "Off: freeflow only works with an empty main hand (fists only).")
	public static boolean allowNonEmptyHand = false;

	// --- Strike ---

	@Setting(category = STRIKES, name = "Strike range", min = 2, max = 24,
			tip = "Max distance (blocks) a strike snaps to an enemy.")
	public static double strikeRange = 10.0;
	@Setting(category = STRIKES, name = "Aim cone (direction held)", min = 10, max = 180,
			tip = "Half-angle in degrees of the targeting cone when you hold a direction.")
	public static double strikeConeDirectional = 60.0;
	@Setting(category = STRIKES, name = "Aim cone (no direction)", min = 10, max = 180,
			tip = "Half-angle in degrees of the targeting cone around the camera's facing.")
	public static double strikeConeNeutral = 90.0;
	@Setting(category = STRIKES, name = "Always-hit distance", min = 0, max = 8,
			tip = "Enemies this close can be struck even outside the cone.")
	public static double strikeCloseRange = 3.0;
	@Setting(category = STRIKES, name = "Strike damage", min = 0, max = 100)
	public static float strikeDamage = 4.0f;
	@Setting(category = STRIKES, name = "Strike knockback", min = 0, max = 5)
	public static double strikeKnockback = 0.35;
	@Setting(category = STRIKES, name = "Heavy hit knockback", min = 0, max = 5,
			tip = "Kicks, haymakers and flying attacks push enemies further.")
	public static double heavyKnockback = 0.8;
	@Setting(category = STRIKES, name = "Heavy hit lift", min = 0, max = 2)
	public static double heavyLift = 0.3;
	@Setting(category = STRIKES, name = "Lunge speed", min = 0.3, max = 5,
			tip = "Blocks per tick when closing in on a far enemy.")
	public static double lungeSpeed = 1.9;
	@Setting(category = STRIKES, name = "Max lunge time", min = 1, max = 30, tip = "Ticks.")
	public static int maxLungeTicks = 7;
	/** Gap (blocks) kept between the player and the target's hitbox edge when the lunge ends. */
	public static double lungeStopGap = 0.8;
	@Setting(category = STRIKES, name = "Recovery after a hit", min = 0, max = 20,
			tip = "Ticks before the next strike starts.")
	public static int strikeRecoveryTicks = 3;
	@Setting(category = STRIKES, name = "Recovery after a miss", min = 0, max = 40, tip = "Ticks.")
	public static int whiffRecoveryTicks = 6;
	/** Extra distance tolerated at hit time (lag / prediction error). */
	public static double hitValidationSlack = 2.5;

	// --- Combo & rhythm ---

	@Setting(category = STRIKES, name = "Combo timeout", min = 5, max = 200,
			tip = "Ticks without a hit before the combo breaks (20 = 1s).")
	public static int comboTimeoutTicks = 30;
	@Setting(category = STRIKES, name = "Combo scaling cap", min = 1, max = 100,
			tip = "Combo count at which damage and range stop growing.")
	public static int comboScalingCap = 20;
	@Setting(category = STRIKES, name = "Damage per combo hit", min = 0, max = 1,
			tip = "0.05 = +5% per hit, so x20 deals double damage.")
	public static float comboDamagePerHit = 0.05f;
	@Setting(category = STRIKES, name = "Range per combo hit", min = 0, max = 2, tip = "Extra blocks of reach per hit.")
	public static double comboRangePerHit = 0.2;
	@Setting(category = STRIKES, name = "Combo hits per faster step", min = 1, max = 100,
			tip = "Every this many hits, recovery after a hit gets one tick shorter.")
	public static int comboRecoveryStep = 10;
	@Setting(category = STRIKES, name = "Rhythm window", min = 0, max = 20,
			tip = "Ticks before a hit lands in which the next press counts as on the beat.")
	public static int rhythmWindowTicks = 3;
	@Setting(category = STRIKES, name = "Mashing allowed", min = 0, max = 20,
			tip = "Off-beat presses tolerated during one strike before you stagger.")
	public static int rhythmSpamLimit = 2;
	@Setting(category = STRIKES, name = "Stagger time", min = 0, max = 60, tip = "Ticks of lost control after mashing.")
	public static int staggerTicks = 8;
	@Setting(category = STRIKES, name = "Stagger breaks combo")
	public static boolean staggerBreaksCombo = true;

	// --- Combat director (enemy turn-taking) ---

	@Setting(category = ENEMIES, name = "Max simultaneous attackers", min = 1, max = 12,
			tip = "Enemies allowed to have an attack in progress against you at once.")
	public static int maxSimultaneousAttackers = 4;
	@Setting(category = ENEMIES, name = "Warning time", min = 4, max = 60,
			tip = "Ticks between the ! appearing and the attack landing: the counter window.")
	public static int telegraphTicks = 12;
	@Setting(category = ENEMIES, name = "Gap between attacks", min = 0, max = 40,
			tip = "Min ticks between two enemies starting their attacks.")
	public static int telegraphGapTicks = 3;
	@Setting(category = ENEMIES, name = "Enemy cooldown (min)", min = 0, max = 400, tip = "Ticks after an enemy attacks.")
	public static int enemyAttackCooldownMin = 25;
	@Setting(category = ENEMIES, name = "Enemy cooldown (max)", min = 0, max = 400)
	public static int enemyAttackCooldownMax = 50;
	@Setting(category = ENEMIES, name = "Circling distance", min = 1.5, max = 12,
			tip = "How far waiting enemies keep while circling you.")
	public static double circleRadius = 3.5;

	// --- Counter ---

	@Setting(category = DEFENCE, name = "Counter range", min = 1, max = 16)
	public static double counterRange = 6.0;
	@Setting(category = DEFENCE, name = "Counter damage", min = 0, max = 100)
	public static float counterDamage = 3.0f;
	@Setting(category = DEFENCE, name = "Counter knockback", min = 0, max = 5)
	public static double counterKnockback = 0.9;
	@Setting(category = DEFENCE, name = "Counter stun time", min = 0, max = 200, tip = "Ticks a countered enemy is stunned.")
	public static int counterStunTicks = 30;
	@Setting(category = DEFENCE, name = "Missed counter lockout", min = 0, max = 60,
			tip = "Ticks you can't counter after pressing it with nothing to counter.")
	public static int counterWhiffTicks = 10;
	@Setting(category = DEFENCE, name = "Throw counter speed", min = 0, max = 3)
	public static double counterThrowSpeed = 0.75;
	@Setting(category = DEFENCE, name = "Throw counter lift", min = 0, max = 2)
	public static double counterThrowLift = 0.5;
	@Setting(category = DEFENCE, name = "Thrown enemy down time", min = 0, max = 400, tip = "Ticks.")
	public static int counterThrowDownTicks = 40;
	@Setting(category = DEFENCE, name = "Creeper kick strength", min = 0, max = 5)
	public static double creeperKickKnockback = 1.6;

	// --- Evade / vault ---

	@Setting(category = DEFENCE, name = "Roll distance", min = 1, max = 12, tip = "Blocks.")
	public static double evadeDistance = 4.0;
	@Setting(category = DEFENCE, name = "Roll duration", min = 3, max = 30, tip = "Ticks.")
	public static int evadeTicks = 9;
	@Setting(category = DEFENCE, name = "Roll invulnerability", min = 0, max = 30, tip = "Ticks.")
	public static int evadeIframeTicks = 8;
	@Setting(category = DEFENCE, name = "Time between rolls", min = 0, max = 40,
			tip = "Ticks before the next roll can start; presses during a roll are ignored.")
	public static int rollRecoveryTicks = 10;
	@Setting(category = DEFENCE, name = "Time after a vault", min = 0, max = 40, tip = "Ticks.")
	public static int vaultRecoveryTicks = 9;
	@Setting(category = DEFENCE, name = "Roll dodges arrows for", min = 0, max = 40, tip = "Ticks.")
	public static int rollProjectileDodgeTicks = 14;
	@Setting(category = DEFENCE, name = "Vault range", min = 1, max = 10,
			tip = "Rolling toward an enemy this close vaults over it.")
	public static double vaultRange = 5.0;
	@Setting(category = DEFENCE, name = "Vault cone", min = 5, max = 180, tip = "Degrees.")
	public static double vaultCone = 50.0;
	/** How far past the enemy the vault lands. */
	public static double vaultLandBehind = 1.8;
	@Setting(category = DEFENCE, name = "Vault duration", min = 3, max = 30, tip = "Ticks.")
	public static int vaultTicks = 7;

	// Running (in fights)
	@Setting(category = DEFENCE, name = "Hold Space to run", client = true,
			tip = "In a fight Space runs (hold) and evades (double-tap) instead of jumping; steps are climbed "
					+ "automatically. Off: Space jumps as usual.")
	public static boolean holdToRun = true;
	@Setting(category = DEFENCE, name = "Run speed bonus", min = 0, max = 1.5,
			tip = "Extra speed while running in a fight, on top of sprinting (0.25 = 25% faster).")
	public static double runSpeedBonus = 0.25;

	// Slide (tap sprint while running)
	@Setting(category = DEFENCE, name = "Slide duration", min = 3, max = 40, tip = "Ticks.")
	public static int slideTicks = 10;
	@Setting(category = DEFENCE, name = "Slide distance", min = 1, max = 16, tip = "Blocks.")
	public static double slideDistance = 6.5;
	@Setting(category = DEFENCE, name = "Time after a slide", min = 0, max = 40, tip = "Ticks.")
	public static int slideCooldownTicks = 6;
	@Setting(category = DEFENCE, name = "Slide invulnerability", min = 0, max = 30, tip = "Ticks.")
	public static int slideIframeTicks = 6;
	@Setting(category = DEFENCE, name = "Slide trip radius", min = 0.2, max = 3,
			tip = "Enemies this close to your path are knocked down.")
	public static double slideTripRadius = 0.9;
	@Setting(category = DEFENCE, name = "Slide damage", min = 0, max = 100)
	public static float slideDamage = 2.0f;
	@Setting(category = DEFENCE, name = "Slide knockdown time", min = 0, max = 400, tip = "Ticks.")
	public static int slideKnockdownTicks = 40;
	/** Double-tapping a movement key evades in that direction (client). */
	@Setting(category = DEFENCE, name = "Double-tap to roll", client = true,
			tip = "Double-tapping a movement key rolls that way.")
	public static boolean doubleTapEvade = true;
	@Setting(category = DEFENCE, name = "Double-tap window", min = 2, max = 20, client = true, tip = "Ticks.")
	public static int doubleTapWindowTicks = 6;

	// --- Stun / beatdown / takedowns ---

	@Setting(category = TAKEDOWNS, name = "Stun range", min = 1, max = 12)
	public static double stunRange = 5.0;
	@Setting(category = TAKEDOWNS, name = "Stun time", min = 0, max = 400, tip = "Ticks (40 = 2s).")
	public static int stunTicks = 40;
	@Setting(category = TAKEDOWNS, name = "Stun cooldown", min = 0, max = 200, tip = "Ticks.")
	public static int stunCooldownTicks = 15;
	@Setting(category = TAKEDOWNS, name = "Missed stun lockout", min = 0, max = 60, tip = "Ticks.")
	public static int stunWhiffTicks = 10;
	@Setting(category = TAKEDOWNS, name = "Beatdown damage", min = 0, max = 100, tip = "Per hit, before combo scaling.")
	public static float beatdownDamage = 2.0f;
	/** Ticks between beatdown hits. */
	public static int beatdownRecoveryTicks = 1;
	@Setting(category = TAKEDOWNS, name = "Beatdown stun extension", min = 0, max = 60,
			tip = "Each beatdown hit keeps the enemy stunned at least this much longer.")
	public static int beatdownStunExtendTicks = 12;
	@Setting(category = TAKEDOWNS, name = "Beatdown hits", min = 1, max = 20,
			tip = "The last hit of a beatdown knocks the enemy down.")
	public static int beatdownFinisherHits = 5;
	@Setting(category = TAKEDOWNS, name = "Finisher knockback", min = 0, max = 5)
	public static double finisherKnockback = 0.4;
	@Setting(category = TAKEDOWNS, name = "Knockdown time", min = 10, max = 400, tip = "Ticks an enemy stays down.")
	public static int knockdownTicks = 60;
	@Setting(category = TAKEDOWNS, name = "Ground takedown range", min = 1, max = 12)
	public static double groundTakedownRange = 6.0;
	/** How far to the side of the downed enemy's chest the player kneels. */
	public static double groundTakedownSideOffset = 0.75;
	@Setting(category = TAKEDOWNS, name = "Ground takedown duration", min = 4, max = 80,
			tip = "Ticks; you are locked and can be hit meanwhile.")
	public static int groundTakedownTicks = 20;
	@Setting(category = TAKEDOWNS, name = "Special takedown cost", min = 1, max = 100, tip = "Combo needed and spent.")
	public static int specialTakedownCost = 8;
	/** A special takedown's blow lands at least this many ticks after the press (its leap). */
	public static int specialTakedownWindupTicks = 5;
	@Setting(category = TAKEDOWNS, name = "Boss health threshold", min = 1, max = 2000,
			tip = "Mobs with more max health count as bosses: takedowns only deal heavy damage.")
	public static float bossHealthThreshold = 50.0f;
	@Setting(category = TAKEDOWNS, name = "Takedown damage to bosses", min = 0, max = 500)
	public static float bossTakedownDamage = 12.0f;

	// --- Enemy roles ---

	@Setting(category = ENEMIES, name = "Swords and axes make mobs armed",
			tip = "Armed mobs' attacks can't be countered (red !): roll away.")
	public static boolean armedFromHeldWeapons = true;
	@Setting(category = ENEMIES, name = "Extra armed mobs",
			tip = "Entity ids, e.g. minecraft:vindicator. Added to the freeflow:armed tag.")
	public static List<String> extraArmed = new ArrayList<>();
	@Setting(category = ENEMIES, name = "Extra ranged mobs", tip = "Entity ids. Their shots must be evaded.")
	public static List<String> extraRanged = new ArrayList<>();
	@Setting(category = ENEMIES, name = "Extra shielded mobs", tip = "Entity ids. Stun them or hit them from behind.")
	public static List<String> extraShielded = new ArrayList<>();
	@Setting(category = ENEMIES, name = "Extra bosses", tip = "Entity ids. Immune to takedowns.")
	public static List<String> extraBoss = new ArrayList<>();
	@Setting(category = ENEMIES, name = "Shield recoil", min = 0, max = 40, tip = "Ticks you recoil when a strike is blocked.")
	public static int shieldRecoilTicks = 8;
	@Setting(category = ENEMIES, name = "Shield block angle", min = 0, max = 180,
			tip = "Degrees around the enemy's facing that its shield covers.")
	public static double shieldBlockAngle = 100.0;
	@Setting(category = ENEMIES, name = "Max simultaneous shooters", min = 0, max = 12)
	public static int maxSimultaneousShooters = 2;
	@Setting(category = ENEMIES, name = "Shot warning time", min = 4, max = 80, tip = "Ticks.")
	public static int rangedTelegraphTicks = 16;
	@Setting(category = ENEMIES, name = "Shooter cooldown (min)", min = 0, max = 400, tip = "Ticks.")
	public static int rangedCooldownMin = 40;
	@Setting(category = ENEMIES, name = "Shooter cooldown (max)", min = 0, max = 400)
	public static int rangedCooldownMax = 80;
	@Setting(category = ENEMIES, name = "Boss stun multiplier", min = 0, max = 1)
	public static float bossStunMultiplier = 0.25f;
	@Setting(category = ENEMIES, name = "Boss snap range", min = 1, max = 24, tip = "Max strike snap distance to a boss.")
	public static double bossSnapRange = 4.0;

	// --- Camera (client) ---

	@Setting(category = CAMERA, name = "Combat camera", client = true, tip = "The over-the-shoulder camera.")
	public static boolean cameraEnabled = true;
	@Setting(category = CAMERA, name = "Use it outside fights", client = true)
	public static boolean cameraEverywhere = false;
	@Setting(category = CAMERA, name = "Distance", min = 1, max = 12, client = true, tip = "Blocks behind you.")
	public static double cameraDistance = 4.0;
	@Setting(category = CAMERA, name = "Shoulder offset", min = -3, max = 3, client = true, tip = "Blocks to the side.")
	public static double cameraShoulderOffset = 0.85;
	@Setting(category = CAMERA, name = "Height", min = -1, max = 3, client = true, tip = "Blocks above your eyes.")
	public static double cameraHeight = 0.3;
	@Setting(category = CAMERA, name = "Follow sharpness", min = 1, max = 60, client = true,
			tip = "How quickly the camera catches up (higher = tighter).")
	public static double cameraFollowSharpness = 14.0;
	@Setting(category = CAMERA, name = "Zoom-out speed", min = 0.5, max = 30, client = true,
			tip = "How quickly the camera eases back out after a wall pushed it in.")
	public static double cameraZoomOutSharpness = 4.0;
	@Setting(category = CAMERA, name = "Body turn speed", min = 5, max = 180, client = true,
			tip = "Degrees per tick your body turns toward where you move.")
	public static float cameraBodyTurnSpeed = 40.0f;

	// --- Camera polish: combat framing (client) ---

	@Setting(category = CAMERA, name = "Pull-back per enemy", min = 0, max = 2, client = true,
			tip = "Extra distance per enemy beyond the first.")
	public static double framingDistancePerEnemy = 0.6;
	@Setting(category = CAMERA, name = "Rise per enemy", min = 0, max = 1, client = true)
	public static double framingHeightPerEnemy = 0.12;
	@Setting(category = CAMERA, name = "FOV per enemy", min = 0, max = 6, client = true, tip = "Degrees.")
	public static double framingFovPerEnemy = 1.5;
	@Setting(category = CAMERA, name = "Framing enemy cap", min = 1, max = 20, client = true,
			tip = "Enemy count at which the framing stops widening.")
	public static int framingMaxEnemies = 6;
	@Setting(category = CAMERA, name = "Lean toward enemies", min = 0, max = 1, client = true,
			tip = "Fraction of the way the camera leans toward the enemies' centre.")
	public static double framingCentroidBias = 0.25;
	@Setting(category = CAMERA, name = "Max lean", min = 0, max = 6, client = true, tip = "Blocks.")
	public static double framingCentroidMaxShift = 2.0;
	@Setting(category = CAMERA, name = "Auto-turn delay", min = 0, max = 200, client = true,
			tip = "Ticks without mouse movement before the camera turns to keep enemies in view.")
	public static int framingAutoYawDelayTicks = 30;
	@Setting(category = CAMERA, name = "Auto-turn threshold", min = 0, max = 180, client = true,
			tip = "Degrees the enemies may drift off-centre before the camera turns.")
	public static double framingAutoYawThreshold = 50.0;
	@Setting(category = CAMERA, name = "Auto-turn speed", min = 0, max = 20, client = true,
			tip = "Degrees per tick; 0 turns auto-turn off.")
	public static float framingAutoYawSpeed = 2.0f;

	// --- Camera polish: cinematics, effects, HUD (client) ---

	@Setting(category = EFFECTS, name = "Cinematic shots", client = true,
			tip = "The camera swings round on takedowns and a fight's final hit.")
	public static boolean cinematicShots = true;
	@Setting(category = EFFECTS, name = "Slow motion", client = true,
			tip = "Real slow motion during cinematic shots (singleplayer only).")
	public static boolean cinematicSlowMo = true;
	@Setting(category = EFFECTS, name = "Slow motion tick rate", min = 1, max = 20, client = true,
			tip = "Game speed during slow motion (20 = normal).")
	public static float cinematicSlowMoTickRate = 8.0f;
	@Setting(category = EFFECTS, name = "Cinematic length", min = 4, max = 60, client = true, tip = "Ticks.")
	public static int cinematicTicks = 16;
	@Setting(category = EFFECTS, name = "Slow motion length", min = 0, max = 60, client = true, tip = "Ticks.")
	public static int cinematicSlowMoTicks = 8;
	@Setting(category = EFFECTS, name = "Hit-stop", client = true, tip = "The picture freezes for an instant on impact.")
	public static boolean hitStop = true;
	@Setting(category = EFFECTS, name = "Hit-stop: light", min = 0, max = 300, client = true, tip = "Milliseconds.")
	public static long hitStopLightMs = 35;
	@Setting(category = EFFECTS, name = "Hit-stop: medium", min = 0, max = 300, client = true, tip = "Milliseconds.")
	public static long hitStopMediumMs = 60;
	@Setting(category = EFFECTS, name = "Hit-stop: heavy", min = 0, max = 300, client = true, tip = "Milliseconds.")
	public static long hitStopHeavyMs = 100;
	@Setting(category = EFFECTS, name = "Camera shake", client = true, tip = "Shake and FOV punch on impact.")
	public static boolean cameraShake = true;
	@Setting(category = EFFECTS, name = "Target marker", client = true,
			tip = "Marks the enemy your next strike would snap to.")
	public static boolean targetIndicator = true;
	@Setting(category = EFFECTS, name = "Hide player when camera is closer than", min = 0, max = 3, client = true,
			tip = "Blocks.")
	public static double hidePlayerDistance = 0.9;

	// --- Controller (client) ---

	@Setting(category = CONTROLLER, name = "Controller support", client = true,
			tip = "Play with a PS5 DualSense (or any gamepad) using the Arkham Knight layout. "
					+ "Turned off automatically when Controlify is installed.")
	public static boolean padEnabled = true;
	@Setting(category = CONTROLLER, name = "Stick deadzone", min = 0, max = 0.6, client = true,
			tip = "Stick movement smaller than this is ignored (stops drift).")
	public static double padDeadzone = 0.12;
	@Setting(category = CONTROLLER, name = "Camera speed", min = 30, max = 900, client = true,
			tip = "Degrees per second with the right stick fully pushed.")
	public static double padLookSpeed = 300.0;
	@Setting(category = CONTROLLER, name = "Camera vertical speed", min = 0.1, max = 2, client = true,
			tip = "Up/down speed as a fraction of the side-to-side speed.")
	public static double padLookPitchScale = 0.6;
	@Setting(category = CONTROLLER, name = "Invert camera up/down", client = true)
	public static boolean padInvertY = false;
	@Setting(category = CONTROLLER, name = "Trigger threshold", min = 0.05, max = 0.95, client = true,
			tip = "How far L2/R2 must be pulled to count as pressed.")
	public static double padTriggerThreshold = 0.4;
}

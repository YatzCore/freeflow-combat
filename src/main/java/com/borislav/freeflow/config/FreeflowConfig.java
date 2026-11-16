package com.borislav.freeflow.config;

/**
 * Tunable values. Plain constants for now; milestone 13 moves these into a
 * Cloth Config file + ModMenu screen without changing call sites.
 */
public final class FreeflowConfig {
	private FreeflowConfig() {}

	/** Radius (blocks) in which a mob targeting the player engages combat mode. */
	public static double combatEngageRadius = 12.0;

	/** Ticks without any engaged mob before AUTO combat mode ends (20 ticks = 1s). */
	public static int combatCalmTicks = 80;

	/** How often (ticks) the server rescans for engaged mobs. */
	public static int combatScanInterval = 4;

	/** If false, freeflow only works with an empty main hand (fists only). */
	public static boolean allowNonEmptyHand = false;

	// --- Strike ---

	/** Max distance (blocks) a strike can snap to a target. */
	public static double strikeRange = 10.0;
	/** Half-angle (degrees) of the targeting cone when a direction is held. */
	public static double strikeConeDirectional = 60.0;
	/** Half-angle (degrees) of the targeting cone with no direction held (uses camera facing). */
	public static double strikeConeNeutral = 90.0;
	/** Enemies this close are always eligible, even outside the cone. */
	public static double strikeCloseRange = 3.0;
	public static float strikeDamage = 4.0f;
	public static double strikeKnockback = 0.35;
	/** Heavy hits (kicks, haymakers, flying attacks) knock enemies back further and lift them. */
	public static double heavyKnockback = 0.8;
	public static double heavyLift = 0.3;
	/** Lunge speed in blocks per tick (20 ticks = 1s). */
	public static double lungeSpeed = 1.9;
	public static int maxLungeTicks = 7;
	/** Gap (blocks) kept between the player and the target's hitbox edge when the lunge ends. */
	public static double lungeStopGap = 0.8;
	/** Ticks after a hit before the next strike starts (buffered presses wait for this). */
	public static int strikeRecoveryTicks = 3;
	/** Ticks of recovery after striking at nothing. */
	public static int whiffRecoveryTicks = 6;
	/** Extra distance tolerated at hit time (lag / prediction error). */
	public static double hitValidationSlack = 2.5;

	// --- Combo & rhythm ---

	/** Ticks without a hit before the combo breaks (30 = 1.5s). */
	public static int comboTimeoutTicks = 30;
	/** Combo count at which scaling stops growing. */
	public static int comboScalingCap = 20;
	/** Extra damage per combo hit (0.05 = +5%, so x20 = double damage). */
	public static float comboDamagePerHit = 0.05f;
	/** Extra strike range (blocks) per combo hit (x20 = 8 + 4 = 12 blocks). */
	public static double comboRangePerHit = 0.2;
	/** Every this many combo hits, the post-hit recovery gets one tick shorter (faster flow). */
	public static int comboRecoveryStep = 10;
	/** A press counts as "on beat" if it comes within this many ticks before the current hit lands (or during recovery). */
	public static int rhythmWindowTicks = 3;
	/** Early (off-beat) presses tolerated during one strike before the player staggers. */
	public static int rhythmSpamLimit = 2;
	/** Stagger duration in ticks (8 = 0.4s); inputs are ignored meanwhile. */
	public static int staggerTicks = 8;
	public static boolean staggerBreaksCombo = true;

	// --- Combat director (enemy turn-taking) ---

	/** Max enemies with an attack in progress against one player at the same time. */
	public static int maxSimultaneousAttackers = 4;
	/** Warning time before an enemy attack lands (12 = 0.6s): the counter window. */
	public static int telegraphTicks = 12;
	/** Min ticks between two enemies starting their attacks. */
	public static int telegraphGapTicks = 3;
	/** Per-enemy cooldown after attacking, randomised between min and max. */
	public static int enemyAttackCooldownMin = 25;
	public static int enemyAttackCooldownMax = 50;
	/** Distance waiting enemies keep while circling. */
	public static double circleRadius = 3.5;

	// --- Counter ---

	/** Telegraphed enemies within this distance are countered. */
	public static double counterRange = 6.0;
	public static float counterDamage = 3.0f;
	public static double counterKnockback = 0.9;
	/** How long a countered enemy is stunned (slowed, can't attack). */
	public static int counterStunTicks = 30;
	/** Lockout after pressing counter with nothing to counter. */
	public static int counterWhiffTicks = 10;
	/** Throw counter: how hard the enemy is flung over the player, and how long it stays down. */
	public static double counterThrowSpeed = 0.75;
	public static double counterThrowLift = 0.5;
	public static int counterThrowDownTicks = 40;
	/** Creeper counter: how hard the creeper is kicked away (its fuse is reset). */
	public static double creeperKickKnockback = 1.6;

	// --- Evade / vault ---

	/** Dodge distance (blocks) and duration (ticks). */
	public static double evadeDistance = 4.0;
	public static int evadeTicks = 9;
	/** Invulnerability after starting an evade or vault. */
	public static int evadeIframeTicks = 8;
	/** Ticks before the next roll can start (= the roll animation length); presses during a roll are ignored. */
	public static int rollRecoveryTicks = 10;
	public static int vaultRecoveryTicks = 9;
	/** Arrows and other projectiles miss for this long after starting a roll. */
	public static int rollProjectileDodgeTicks = 14;
	/** Evading toward an enemy this close (and within the cone) vaults over it instead. */
	public static double vaultRange = 5.0;
	public static double vaultCone = 50.0;
	/** How far past the enemy the vault lands. */
	public static double vaultLandBehind = 1.8;
	public static int vaultTicks = 7;
	/** Double-tapping a movement key evades in that direction (client). */
	// Slide (tap sprint while running)
	public static int slideTicks = 10;
	public static double slideDistance = 6.5;
	public static int slideCooldownTicks = 6;
	public static int slideIframeTicks = 6;
	public static double slideTripRadius = 0.9;
	public static float slideDamage = 2.0f;
	public static int slideKnockdownTicks = 40;
	public static boolean doubleTapEvade = true;
	public static int doubleTapWindowTicks = 6;

	// --- Stun / beatdown / takedowns ---

	public static double stunRange = 5.0;
	/** How long a stun lasts (40 = 2s). */
	public static int stunTicks = 40;
	public static int stunCooldownTicks = 15;
	/** Lockout after stunning at nothing. */
	public static int stunWhiffTicks = 10;
	/** Damage per beatdown hit (before combo scaling). Mashing is allowed during a beatdown. */
	public static float beatdownDamage = 2.0f;
	public static int beatdownRecoveryTicks = 1;
	/** Each beatdown hit keeps the target stunned at least this much longer. */
	public static int beatdownStunExtendTicks = 12;
	/** The Nth beatdown hit is a finisher that knocks the enemy down. */
	public static int beatdownFinisherHits = 5;
	public static double finisherKnockback = 0.4;
	/** How long a knocked-down enemy stays down (60 = 3s). */
	public static int knockdownTicks = 60;
	public static double groundTakedownRange = 6.0;
	/** How far to the side of the downed enemy's chest the player kneels. */
	public static double groundTakedownSideOffset = 0.75;
	/** Ground takedown duration (the player is locked and vulnerable meanwhile). */
	public static int groundTakedownTicks = 20;
	/** Combo needed (and spent) for a special takedown. */
	public static int specialTakedownCost = 8;
	/** Mobs with more max health than this are "bosses": takedowns only deal heavy damage. */
	public static float bossHealthThreshold = 50.0f;
	public static float bossTakedownDamage = 12.0f;

	// --- Enemy roles ---

	/** Mobs holding a sword or axe count as ARMED (uncounterable attacks). */
	public static boolean armedFromHeldWeapons = true;
	/** Recoil (ticks) when a strike bounces off a shield. */
	public static int shieldRecoilTicks = 8;
	/** A shield blocks strikes coming from within this angle of the enemy's facing. */
	public static double shieldBlockAngle = 100.0;
	/** Ranged enemies allowed to have a shot lined up at the same time. */
	public static int maxSimultaneousShooters = 2;
	/** Warning time before a ranged shot (longer than melee: easier to read). */
	public static int rangedTelegraphTicks = 16;
	public static int rangedCooldownMin = 40;
	public static int rangedCooldownMax = 80;
	/** Bosses: stun duration multiplier and max strike snap distance. */
	public static float bossStunMultiplier = 0.25f;
	public static double bossSnapRange = 4.0;

	// --- Camera (client) ---

	/** Master switch for the over-the-shoulder combat camera. */
	public static boolean cameraEnabled = true;
	/** If true the camera is used all the time, not only while freeflow is active. */
	public static boolean cameraEverywhere = false;
	/** Distance (blocks) behind the player. */
	public static double cameraDistance = 4.0;
	/** Sideways shoulder offset (blocks). */
	public static double cameraShoulderOffset = 0.85;
	/** Extra height above the eyes (blocks). */
	public static double cameraHeight = 0.3;
	/** How quickly the camera catches up with the player (higher = tighter). */
	public static double cameraFollowSharpness = 14.0;
	/** How quickly the camera eases back out after being pushed in by a wall. */
	public static double cameraZoomOutSharpness = 4.0;
	/** Max degrees per tick the player body turns toward the movement direction. */
	public static float cameraBodyTurnSpeed = 40.0f;

	// --- Camera polish: combat framing (client) ---

	/** Extra camera distance per nearby enemy beyond the first (4 -> ~7 blocks). */
	public static double framingDistancePerEnemy = 0.6;
	public static double framingHeightPerEnemy = 0.12;
	/** Extra FOV degrees per enemy beyond the first. */
	public static double framingFovPerEnemy = 1.5;
	/** Enemy count at which framing stops widening. */
	public static int framingMaxEnemies = 6;
	/** Fraction of the way the camera pivot leans toward the enemies' centre (capped). */
	public static double framingCentroidBias = 0.25;
	public static double framingCentroidMaxShift = 2.0;
	/** After this long without mouse movement, the camera slowly turns to keep enemies in view. */
	public static int framingAutoYawDelayTicks = 30;
	/** Enemies' centre may drift this far (degrees) off the camera's facing before it turns. */
	public static double framingAutoYawThreshold = 50.0;
	/** Max auto-turn speed, degrees per tick. */
	public static float framingAutoYawSpeed = 2.0f;

	// --- Camera polish: cinematics, target indicator ---

	public static boolean cinematicShots = true;
	/** Real slow motion during cinematic shots (singleplayer only: it changes the server tick rate). */
	public static boolean cinematicSlowMo = true;
	public static float cinematicSlowMoTickRate = 8.0f;
	/** Cinematic shot length and how much of it is in slow motion (client ticks). */
	public static int cinematicTicks = 16;
	public static int cinematicSlowMoTicks = 8;
	/** Hit-stop: the game freezes for a few ms on impact (client-side, per strength). */
	public static boolean hitStop = true;
	public static long hitStopLightMs = 35;
	public static long hitStopMediumMs = 60;
	public static long hitStopHeavyMs = 100;
	/** Camera shake + FOV punch on impact. */
	public static boolean cameraShake = true;
	/** Show a marker on the enemy your next strike would snap to. */
	public static boolean targetIndicator = true;
	/** Hide the player model when the camera is pushed closer than this (blocks). */
	public static double hidePlayerDistance = 0.9;
}

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
	public static double strikeRange = 8.0;
	/** Half-angle (degrees) of the targeting cone when a direction is held. */
	public static double strikeConeDirectional = 60.0;
	/** Half-angle (degrees) of the targeting cone with no direction held (uses camera facing). */
	public static double strikeConeNeutral = 90.0;
	/** Enemies this close are always eligible, even outside the cone. */
	public static double strikeCloseRange = 2.5;
	public static float strikeDamage = 4.0f;
	public static double strikeKnockback = 0.35;
	/** Lunge speed in blocks per tick (20 ticks = 1s). */
	public static double lungeSpeed = 1.6;
	public static int maxLungeTicks = 6;
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
	public static int maxSimultaneousAttackers = 2;
	/** Warning time before an enemy attack lands (12 = 0.6s): the counter window. */
	public static int telegraphTicks = 12;
	/** Min ticks between two enemies starting their attacks. */
	public static int telegraphGapTicks = 5;
	/** Per-enemy cooldown after attacking, randomised between min and max. */
	public static int enemyAttackCooldownMin = 25;
	public static int enemyAttackCooldownMax = 50;
	/** Distance waiting enemies keep while circling. */
	public static double circleRadius = 3.5;

	// --- Counter ---

	/** Telegraphed enemies within this distance are countered. */
	public static double counterRange = 5.0;
	public static float counterDamage = 3.0f;
	public static double counterKnockback = 0.9;
	/** How long a countered enemy is stunned (slowed, can't attack). */
	public static int counterStunTicks = 30;
	/** Lockout after pressing counter with nothing to counter. */
	public static int counterWhiffTicks = 10;

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
}

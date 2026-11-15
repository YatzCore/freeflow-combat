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

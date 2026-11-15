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
}

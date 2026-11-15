package com.borislav.freeflow.client;

import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatEventPayload;
import com.borislav.freeflow.network.ComboPayload;
import com.borislav.freeflow.network.StrikeStartPayload;

/**
 * Client view of the combo and rhythm, driven by server packets and a local tick
 * counter (so HUD timers stay smooth without per-tick packets).
 */
public final class ClientCombo {
	private ClientCombo() {}

	/** Local client tick counter. */
	public static long clientTick = 0;

	public static int combo = 0;
	public static long comboExpireTick = 0;

	public static long staggerUntilTick = 0;

	/** Combo value that just broke (shown fading out), and when. */
	public static int brokenCombo = 0;
	public static long brokenAtTick = -1000;
	/** When the combo last increased (drives the "pop" animation). */
	public static long lastHitTick = -1000;
	public static boolean lastBreakWasStagger = false;

	/** Rhythm: client tick at which the current strike lands, or -1. */
	public static long strikeLandTick = -1;
	public static long strikeStartTick = -1;

	/** Last successful counter (for the "COUNTER" flash) and how many were chained back to back. */
	public static long lastCounterTick = -1000;
	public static int lastCounterCount = 0;

	public static void onCounter(int count) {
		if (count > 0) {
			lastCounterCount = clientTick - lastCounterTick <= 15 ? lastCounterCount + count : count;
			lastCounterTick = clientTick;
			strikeLandTick = -1;
		}
	}

	/** Generic centre-screen flash (TAKEDOWN, KNOCKDOWN, ...). */
	public static String flashText = "";
	public static int flashColor = 0xFFFFFF;
	public static long flashTick = -1000;
	/** Player movement is locked until this client tick (ground takedown). */
	public static long lockedUntilTick = 0;

	public static void flash(String text, int rgb) {
		flashText = text;
		flashColor = rgb;
		flashTick = clientTick;
	}

	public static boolean isLocked() {
		return clientTick < lockedUntilTick;
	}

	public static void onEvent(CombatEventPayload payload) {
		switch (payload.event()) {
			case KNOCKDOWN -> flash("KNOCKDOWN", 0xFF9A2E);
			case TAKEDOWN_START -> lockedUntilTick = clientTick + payload.ticks();
			case TAKEDOWN -> {
				lockedUntilTick = 0;
				flash("TAKEDOWN", 0xFF4A3D);
			}
			case TAKEDOWN_INTERRUPTED -> {
				lockedUntilTick = 0;
				flash("INTERRUPTED", 0x9A9A9A);
			}
			case SPECIAL_TAKEDOWN -> flash("SPECIAL TAKEDOWN", 0xFF4A3D);
			default -> {}
		}
	}

	public static void tick() {
		clientTick++;
	}

	public static void onCombo(ComboPayload payload) {
		switch (payload.event()) {
			case HIT -> lastHitTick = clientTick;
			case BREAK, STAGGER -> {
				if (combo > 0) {
					brokenCombo = combo;
					brokenAtTick = clientTick;
				}
				lastBreakWasStagger = payload.event() == ComboPayload.Event.STAGGER;
				if (lastBreakWasStagger && combo == 0) {
					brokenAtTick = clientTick; // still flash "STAGGERED"
					brokenCombo = 0;
				}
			}
		}
		combo = payload.combo();
		comboExpireTick = clientTick + payload.ticksLeft();
		staggerUntilTick = clientTick + payload.staggerTicks();
		if (payload.staggerTicks() > 0) {
			strikeLandTick = -1;
		}
	}

	public static void onStrikeStart(StrikeStartPayload payload) {
		strikeStartTick = clientTick;
		strikeLandTick = payload.targetId() >= 0 ? clientTick + Math.max(1, payload.travelTicks()) : -1;
	}

	public static boolean isStaggered() {
		return clientTick < staggerUntilTick;
	}

	/** True if pressing strike right now would be on-beat (same rule as the server). */
	public static boolean isOnBeat() {
		if (strikeLandTick < 0 || clientTick >= strikeLandTick) {
			return true; // idle or recovering
		}
		return strikeLandTick - clientTick <= FreeflowConfig.rhythmWindowTicks;
	}

	/** 0..1 fraction of combo time remaining. */
	public static float comboTimeFraction(float partialTick) {
		if (combo == 0) {
			return 0f;
		}
		float left = comboExpireTick - (clientTick + partialTick);
		return Math.max(0f, Math.min(1f, left / FreeflowConfig.comboTimeoutTicks));
	}

	public static void reset() {
		combo = 0;
		brokenCombo = 0;
		brokenAtTick = -1000;
		lastHitTick = -1000;
		staggerUntilTick = 0;
		strikeLandTick = -1;
		lockedUntilTick = 0;
		flashTick = -1000;
	}
}

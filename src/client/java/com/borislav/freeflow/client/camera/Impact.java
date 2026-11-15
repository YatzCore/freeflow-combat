package com.borislav.freeflow.client.camera;

import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side hit feedback: hit-stop (the game freezes for a few milliseconds on impact),
 * camera shake (trauma model: shake = trauma^2, decaying) and a quick FOV punch.
 * Strength: 1 light, 2 medium, 3 heavy.
 */
public final class Impact {
	private Impact() {}

	private static final long MAX_FREEZE_NANOS = 150_000_000L; // safety cap

	private static long freezeUntilNanos = 0;
	private static float trauma = 0;
	private static float fovPunch = 0;
	private static long lastFrameNanos = System.nanoTime();

	public static void hit(int strength) {
		int s = Mth.clamp(strength, 1, 3);
		if (FreeflowConfig.hitStop) {
			long ms = switch (s) {
				case 1 -> FreeflowConfig.hitStopLightMs;
				case 2 -> FreeflowConfig.hitStopMediumMs;
				default -> FreeflowConfig.hitStopHeavyMs;
			};
			long until = System.nanoTime() + Math.min(ms * 1_000_000L, MAX_FREEZE_NANOS);
			freezeUntilNanos = Math.max(freezeUntilNanos, until);
		}
		if (FreeflowConfig.cameraShake) {
			trauma = Math.min(1.0f, trauma + 0.25f * s);
			fovPunch = Math.min(fovPunch + 1.5f * s, 6.0f);
		}
	}

	/** True while the game should be frozen for hit-stop. */
	public static boolean isFrozen() {
		return System.nanoTime() < freezeUntilNanos;
	}

	/** Advances decay; call once per rendered frame. */
	public static void frame() {
		long now = System.nanoTime();
		float dt = Mth.clamp((now - lastFrameNanos) / 1.0e9f, 0f, 0.1f);
		lastFrameNanos = now;
		trauma = Math.max(0f, trauma - dt * 2.2f);
		fovPunch = Math.max(0f, fovPunch - dt * 18f);
	}

	/** Camera position offset for the shake (blocks). */
	public static Vec3 shakeOffset() {
		if (trauma <= 0) {
			return Vec3.ZERO;
		}
		float shake = trauma * trauma * 0.18f;
		double t = System.nanoTime() / 1.0e9;
		return new Vec3(
				shake * Math.sin(t * 61.0) * Math.cos(t * 17.0),
				shake * Math.sin(t * 53.0 + 1.3),
				shake * Math.cos(t * 47.0 + 2.1));
	}

	/** Degrees subtracted from the FOV (a quick zoom-in punch). */
	public static float fovPunch() {
		return fovPunch;
	}

	public static void reset() {
		freezeUntilNanos = 0;
		trauma = 0;
		fovPunch = 0;
	}
}

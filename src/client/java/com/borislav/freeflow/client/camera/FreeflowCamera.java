package com.borislav.freeflow.client.camera;

import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Over-the-shoulder orbit camera. The mouse orbits the camera (its own yaw/pitch,
 * independent of the player body); the camera follows the player with spring-like
 * smoothing and is pulled in by walls.
 *
 * Vanilla is put in THIRD_PERSON_BACK while active (so the player model renders),
 * and {@code CameraMixin} overrides the final camera position/rotation.
 */
public final class FreeflowCamera {
	private FreeflowCamera() {}

	private static boolean active = false;
	/** Toggled by the camera key; lets the player opt out without editing config. */
	public static boolean userEnabled = true;

	private static float yaw;
	private static float pitch;
	private static CameraType previousType;

	/** +1 = right shoulder, -1 = left shoulder. */
	private static int shoulderSide = 1;
	private static double smoothedSide = 1;

	private static Vec3 smoothedPivot;
	private static double currentDistance;
	private static long lastFrameNanos;

	public static boolean isActive() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static double currentDistance() {
		return currentDistance;
	}

	public static int shoulderSide() {
		return shoulderSide;
	}

	/** Programmatic camera turn (auto-framing). */
	public static void addYaw(float degrees) {
		yaw += degrees;
	}

	public static void swapShoulder() {
		shoulderSide = -shoulderSide;
	}

	/** Called every client tick: enters/leaves the camera and keeps vanilla in third person. */
	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		boolean should = player != null && FreeflowConfig.cameraEnabled && userEnabled && !player.isSpectator()
				&& (FreeflowConfig.cameraEverywhere || ClientCombatState.isFreeflowActive());

		if (should && !active) {
			enter(mc, player);
		} else if (!should && active) {
			exit(mc);
		}

		if (active && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // F5 is locked while orbiting
		}
	}

	private static void enter(Minecraft mc, LocalPlayer player) {
		active = true;
		previousType = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		yaw = player.getYRot();
		pitch = player.getXRot();
		smoothedPivot = null;
		smoothedSide = shoulderSide;
		currentDistance = 0.5; // starts close and eases out: a short "pull back" on entering combat
		lastFrameNanos = System.nanoTime();
	}

	/** Leaves the orbit camera, handing the camera's view direction back to the player. */
	public static void exit(Minecraft mc) {
		if (!active) {
			return;
		}
		active = false;
		mc.options.setCameraType(previousType != null ? previousType : CameraType.FIRST_PERSON);
		LocalPlayer player = mc.player;
		if (player != null) {
			player.setYRot(yaw);
			player.setXRot(pitch);
			player.yRotO = yaw;
			player.xRotO = pitch;
		}
	}

	/** Mouse look, routed here instead of rotating the player. Same scale as vanilla Entity.turn. */
	public static void turn(double dYaw, double dPitch) {
		if (Math.abs(dYaw) + Math.abs(dPitch) > 0.5) {
			CombatFraming.onMouseTurn();
		}
		yaw += (float) dYaw * 0.15f;
		pitch = Mth.clamp(pitch + (float) dPitch * 0.15f, -89.0f, 89.0f);
	}

	/** Computes the camera position for this frame. Called from CameraMixin after vanilla setup. */
	public static Vec3 computePosition(BlockGetter level, Entity entity, float partialTick) {
		long now = System.nanoTime();
		double dt = Mth.clamp((now - lastFrameNanos) / 1.0e9, 0.0, 0.1);
		lastFrameNanos = now;

		Vec3 eye = entity.getEyePosition(partialTick);
		// Lean toward the enemies' centre, but never through a wall.
		Vec3 shift = CombatFraming.shift();
		double shiftLen = shift.length();
		if (shiftLen > 0.05) {
			double clear = CameraCollision.maxDistance(level, entity, eye, eye.add(shift));
			eye = eye.add(shift.scale(Math.min(1.0, clear / shiftLen)));
		}
		if (smoothedPivot == null || smoothedPivot.distanceToSqr(eye) > 64.0) {
			smoothedPivot = eye; // first frame or teleport: snap
		} else {
			smoothedPivot = smoothedPivot.lerp(eye, 1.0 - Math.exp(-FreeflowConfig.cameraFollowSharpness * dt));
		}
		smoothedSide += (shoulderSide - smoothedSide) * (1.0 - Math.exp(-10.0 * dt));

		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		float yawRad = yaw * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yawRad), 0.0, -Mth.sin(yawRad));

		Vec3 desired = smoothedPivot
				.add(right.scale(FreeflowConfig.cameraShoulderOffset * smoothedSide))
				.add(0.0, FreeflowConfig.cameraHeight + CombatFraming.heightBonus(), 0.0)
				.subtract(forward.scale(FreeflowConfig.cameraDistance + CombatFraming.distanceBonus()));

		Vec3 offset = desired.subtract(smoothedPivot);
		double fullLength = offset.length();
		double allowed = CameraCollision.maxDistance(level, entity, smoothedPivot, desired);

		if (allowed < currentDistance) {
			currentDistance = allowed; // walls push in instantly
		} else {
			currentDistance += (allowed - currentDistance) * (1.0 - Math.exp(-FreeflowConfig.cameraZoomOutSharpness * dt));
		}

		return fullLength < 1.0e-4 ? smoothedPivot : smoothedPivot.add(offset.scale(currentDistance / fullLength));
	}
}

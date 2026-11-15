package com.borislav.freeflow.client.camera;

import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Camera-relative movement: WASD is interpreted relative to the orbit camera,
 * the player body turns (smoothly) to face the movement direction, and the
 * input is re-expressed relative to the body so the player moves exactly
 * where the stick/keys point.
 */
public final class RelativeMovement {
	private RelativeMovement() {}

	/** Raw movement input of the last tick, before remapping (-1..1, sneaking already applied). */
	public static float rawForward;
	public static float rawLeft;

	public static void onInputTick(Input input) {
		rawForward = input.forwardImpulse;
		rawLeft = input.leftImpulse;

		if (!FreeflowCamera.isActive()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		float fwd = input.forwardImpulse;
		float left = input.leftImpulse;
		float mag = Math.min(1.0f, Mth.sqrt(fwd * fwd + left * left));
		if (mag < 1.0e-3f) {
			return; // standing still: body keeps its facing
		}

		float targetYaw = worldYaw(FreeflowCamera.yaw(), fwd, left);
		float bodyYaw = player.getYRot();
		float newYaw = bodyYaw + Mth.clamp(Mth.wrapDegrees(targetYaw - bodyYaw),
				-FreeflowConfig.cameraBodyTurnSpeed, FreeflowConfig.cameraBodyTurnSpeed);
		player.setYRot(newYaw);
		player.setXRot(Mth.clamp(FreeflowCamera.pitch(), -30.0f, 30.0f));

		// Re-express the desired world direction in the (new) body frame.
		float delta = (newYaw - targetYaw) * Mth.DEG_TO_RAD;
		input.forwardImpulse = mag * Mth.cos(delta);
		input.leftImpulse = mag * Mth.sin(delta);
		input.up = input.forwardImpulse > 0.1f;
		input.down = input.forwardImpulse < -0.1f;
		input.left = input.leftImpulse > 0.1f;
		input.right = input.leftImpulse < -0.1f;
	}

	/**
	 * World-space yaw of a movement input (forward/left) relative to a reference yaw.
	 * Matches vanilla Entity.moveRelative: forward = (-sin, cos), left = (cos, sin).
	 */
	public static float worldYaw(float referenceYaw, float forward, float left) {
		float r = referenceYaw * Mth.DEG_TO_RAD;
		double dx = left * Mth.cos(r) - forward * Mth.sin(r);
		double dz = forward * Mth.cos(r) + left * Mth.sin(r);
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
}

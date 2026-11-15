package com.borislav.freeflow.client;

import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.network.EvadePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the local player through an evade or vault (movement is client-side).
 * Evade: fast ground dash. Vault: dash through the enemy with a hop, then turn to
 * face it on landing so the next strike flows naturally.
 */
public final class EvadeController {
	private EvadeController() {}

	private static Vec3 step = Vec3.ZERO;
	private static int ticksLeft = 0;
	private static boolean vault = false;
	@Nullable
	private static Entity vaultTarget;
	private static boolean firstTick = false;

	public static boolean isActive() {
		return ticksLeft > 0;
	}

	public static void onEvade(EvadePayload payload) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		LungeController.reset(); // a dodge cancels any lunge
		float r = payload.yaw() * Mth.DEG_TO_RAD;
		double perTick = payload.distance() / Math.max(1, payload.ticks());
		step = new Vec3(-Mth.sin(r) * perTick, 0.0, Mth.cos(r) * perTick);
		ticksLeft = Math.max(1, payload.ticks());
		vault = payload.isVault();
		vaultTarget = vault ? mc.level.getEntity(payload.vaultTargetId()) : null;
		firstTick = true;
		if (vault) {
			player.setYRot(payload.yaw()); // vault faces the enemy it flips over
		}
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (ticksLeft <= 0 || player == null) {
			return;
		}
		double y = player.getDeltaMovement().y;
		if (firstTick && vault && player.onGround()) {
			y = 0.42; // hop over the enemy
		}
		firstTick = false;
		player.setDeltaMovement(step.x, y, step.z);

		if (--ticksLeft == 0) {
			if (vault && vaultTarget != null && vaultTarget.isAlive()) {
				float yaw = Directions.yawTo(player, vaultTarget);
				player.setYRot(yaw);
				player.setYHeadRot(yaw);
				player.setYBodyRot(yaw);
			}
			vaultTarget = null;
		}
	}

	public static void reset() {
		ticksLeft = 0;
		vaultTarget = null;
	}
}

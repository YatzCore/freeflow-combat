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
 * Drives the local player through rolls, vaults, back-flips and slides (movement is
 * client-side). Rolls and slides turn the player to face the way they go (so the roll and
 * slide animations point the right way); vaults hop through the enemy and land facing it;
 * slides start fast and slow down.
 */
public final class EvadeController {
	private EvadeController() {}

	private static Vec3 direction = Vec3.ZERO;
	private static double distance;
	private static int totalTicks;
	private static int ticksLeft = 0;
	private static int mode;
	private static float yaw;
	@Nullable
	private static Entity vaultTarget;
	private static boolean firstTick = false;

	public static boolean isActive() {
		return ticksLeft > 0;
	}

	public static boolean isSliding() {
		return ticksLeft > 0 && mode == EvadePayload.SLIDE;
	}

	public static void onEvade(EvadePayload payload) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		LungeController.reset(); // a dodge cancels any lunge
		yaw = payload.yaw();
		float r = yaw * Mth.DEG_TO_RAD;
		direction = new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
		distance = payload.distance();
		totalTicks = Math.max(1, payload.ticks());
		ticksLeft = totalTicks;
		mode = payload.mode();
		vaultTarget = payload.isVault() ? mc.level.getEntity(payload.vaultTargetId()) : null;
		firstTick = true;
		if (mode != EvadePayload.BACKFLIP) {
			face(player, yaw); // roll / slide / vault animations play facing the travel direction
		}
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (ticksLeft <= 0 || player == null) {
			return;
		}
		int i = totalTicks - ticksLeft; // 0-based tick index
		double step;
		if (mode == EvadePayload.SLIDE) {
			// Linearly decaying speed: weights n, n-1, ..., 1 (sum n(n+1)/2).
			double weight = totalTicks - i;
			step = distance * weight / (totalTicks * (totalTicks + 1) / 2.0);
		} else {
			step = distance / totalTicks;
		}

		double y = player.getDeltaMovement().y;
		if (firstTick && mode == EvadePayload.VAULT && player.onGround()) {
			y = 0.45; // hop over the enemy
		} else if (firstTick && mode == EvadePayload.BACKFLIP && player.onGround()) {
			y = 0.3;
		}
		firstTick = false;
		player.setDeltaMovement(direction.x * step, y, direction.z * step);
		if (mode == EvadePayload.ROLL || mode == EvadePayload.SLIDE) {
			face(player, yaw);
		}

		if (--ticksLeft == 0) {
			if (mode == EvadePayload.VAULT && vaultTarget != null && vaultTarget.isAlive()) {
				face(player, Directions.yawTo(player, vaultTarget));
			}
			vaultTarget = null;
		}
	}

	private static void face(LocalPlayer player, float yaw) {
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
	}

	public static void reset() {
		ticksLeft = 0;
		vaultTarget = null;
	}
}

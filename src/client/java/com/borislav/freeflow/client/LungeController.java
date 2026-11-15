package com.borislav.freeflow.client;

import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.StrikeStartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Moves the local player during a strike lunge. Player movement is client-authoritative
 * in Minecraft, so the dash happens here; the server only decides target and hit timing.
 * Runs at the start of each client tick, before the player's own movement tick.
 */
public final class LungeController {
	private LungeController() {}

	@Nullable
	private static Entity target;
	private static int ticksLeft;
	/** Name of the last snapped target, for the debug overlay. */
	public static String lastTargetName = "-";

	public static boolean isLunging() {
		return target != null && ticksLeft > 0;
	}

	public static void onStrikeStart(StrikeStartPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		Entity entity = payload.targetId() >= 0 ? mc.level.getEntity(payload.targetId()) : null;
		if (entity == null) {
			target = null;
			ticksLeft = 0;
			lastTargetName = "(whiff)";
			return;
		}
		lastTargetName = entity.getName().getString();
		target = entity;
		ticksLeft = payload.travelTicks();
		face(mc.player, entity);
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (target == null || player == null) {
			return;
		}
		if (!target.isAlive() || target.level() != player.level() || ticksLeft <= 0) {
			stop(player);
			return;
		}

		face(player, target);
		Vec3 toTarget = new Vec3(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ());
		double remaining = toTarget.length() - (target.getBbWidth() / 2.0 + FreeflowConfig.lungeStopGap);
		if (remaining <= 0.05) {
			stop(player);
			return;
		}

		// Cover the remaining distance evenly over the remaining ticks.
		Vec3 step = toTarget.normalize().scale(remaining / ticksLeft);
		player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
		ticksLeft--;
		if (ticksLeft == 0) {
			target = null; // velocity from this tick still applies; next tick it decays by friction
		}
	}

	private static void stop(LocalPlayer player) {
		target = null;
		ticksLeft = 0;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
	}

	private static void face(LocalPlayer player, Entity entity) {
		float yaw = Directions.yawTo(player, entity);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
	}

	public static void reset() {
		target = null;
		ticksLeft = 0;
		lastTargetName = "-";
	}
}

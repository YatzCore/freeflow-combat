package com.borislav.freeflow.client.camera;

import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Short cinematic camera swings for big moments (takedowns, the final hit of a fight):
 * the camera eases to a side-on angle framing the player and the target, holds, and
 * eases back. In singleplayer it also slows the game down (server tick rate) for the
 * first part of the shot; in multiplayer the shot is camera-only.
 */
public final class CinematicShots {
	private CinematicShots() {}

	public record Pose(Vec3 position, float yaw, float pitch) {}

	private static int targetId = -1;
	private static long startTick = -1000;
	private static int duration = 0;
	private static int side = 1;
	private static boolean slowMoActive = false;
	private static float savedTickRate = 20.0f;

	public static void start(int entityId) {
		if (!FreeflowConfig.cinematicShots || !FreeflowCamera.isActive()) {
			return;
		}
		targetId = entityId;
		startTick = ClientCombo.clientTick;
		duration = FreeflowConfig.cinematicTicks;
		side = -FreeflowCamera.shoulderSide(); // swing to the open side
		setSlowMo(true);
	}

	/** Blend weight 0..1: eases in over 3 ticks and out over the last 4. */
	public static float weight(float partialTick) {
		float t = ClientCombo.clientTick + partialTick - startTick;
		if (targetId < 0 || t < 0 || t > duration) {
			return 0f;
		}
		return Mth.clamp(Math.min(t / 3.0f, (duration - t) / 4.0f), 0f, 1f);
	}

	public static boolean isActive() {
		return weight(0) > 0f;
	}

	public static void tick() {
		if (targetId < 0) {
			return;
		}
		long t = ClientCombo.clientTick - startTick;
		if (slowMoActive && t >= FreeflowConfig.cinematicSlowMoTicks) {
			setSlowMo(false);
		}
		if (t > duration) {
			targetId = -1;
		}
	}

	/** Blends the normal camera pose toward the cinematic one; returns null when no shot is playing. */
	@Nullable
	public static Pose apply(BlockGetter level, Entity player, Vec3 pos, float yaw, float pitch, float partialTick) {
		float w = weight(partialTick);
		Minecraft mc = Minecraft.getInstance();
		Entity target = targetId >= 0 && mc.level != null ? mc.level.getEntity(targetId) : null;
		if (w <= 0f || target == null) {
			return null;
		}

		Vec3 playerEye = player.getEyePosition(partialTick);
		Vec3 targetMid = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.6, 0);
		Vec3 centre = playerEye.lerp(targetMid, 0.5);

		// Side-on: perpendicular to the player->target line, a little above, a bit pulled back.
		float lineYaw = Directions.yawTo(player, target);
		float perpYaw = (lineYaw + 90.0f * side) * Mth.DEG_TO_RAD;
		Vec3 desired = centre.add(-Mth.sin(perpYaw) * 3.4, 0.7, Mth.cos(perpYaw) * 3.4);
		double len = desired.distanceTo(centre);
		double clear = CameraCollision.maxDistance(level, player, centre, desired);
		Vec3 shotPos = centre.add(desired.subtract(centre).scale(Math.min(1.0, clear / len)));

		Vec3 look = centre.subtract(shotPos);
		float shotYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float shotPitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));

		return new Pose(pos.lerp(shotPos, w), yaw + Mth.wrapDegrees(shotYaw - yaw) * w, Mth.lerp(w, pitch, shotPitch));
	}

	private static void setSlowMo(boolean on) {
		if (!FreeflowConfig.cinematicSlowMo || on == slowMoActive) {
			return;
		}
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null || server.isPublished()) {
			return; // multiplayer / LAN: never change the shared game speed
		}
		slowMoActive = on;
		if (on) {
			server.execute(() -> {
				savedTickRate = server.tickRateManager().tickrate();
				server.tickRateManager().setTickRate(FreeflowConfig.cinematicSlowMoTickRate);
			});
		} else {
			server.execute(() -> server.tickRateManager().setTickRate(savedTickRate));
		}
	}

	public static void reset() {
		setSlowMo(false);
		targetId = -1;
	}
}

package com.borislav.freeflow.client.camera;

import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Arkham-style fight framing: the more enemies around, the further back, higher and
 * wider the camera goes; its pivot leans toward the enemies' centre, and if the player
 * leaves the mouse alone the camera slowly turns to keep the group in view.
 */
public final class CombatFraming {
	private CombatFraming() {}

	private static double distanceBonus = 0;
	private static double heightBonus = 0;
	private static double fovBonus = 0;
	private static Vec3 shift = Vec3.ZERO;
	private static long lastMouseTick = -1000;
	private static int enemies = 0;

	public static double distanceBonus() {
		return distanceBonus;
	}

	public static double heightBonus() {
		return heightBonus;
	}

	public static double fovBonus() {
		return fovBonus;
	}

	public static Vec3 shift() {
		return shift;
	}

	public static int enemies() {
		return enemies;
	}

	/** Called when the player moves the mouse: pauses auto-turning. */
	public static void onMouseTurn() {
		lastMouseTick = ClientCombo.clientTick;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		double targetDist = 0;
		double targetHeight = 0;
		double targetFov = 0;
		Vec3 targetShift = Vec3.ZERO;
		enemies = 0;

		if (player != null && mc.level != null && FreeflowCamera.isActive()) {
			List<Mob> nearby = mc.level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10.0),
					m -> m.isAlive() && m instanceof Enemy && m.distanceTo(player) <= 10.0);
			enemies = nearby.size();
			if (enemies > 0) {
				int extra = Math.min(enemies, FreeflowConfig.framingMaxEnemies) - 1;
				targetDist = extra * FreeflowConfig.framingDistancePerEnemy;
				targetHeight = extra * FreeflowConfig.framingHeightPerEnemy;
				targetFov = extra * FreeflowConfig.framingFovPerEnemy;

				double cx = 0;
				double cz = 0;
				for (Mob m : nearby) {
					cx += m.getX();
					cz += m.getZ();
				}
				cx /= enemies;
				cz /= enemies;
				Vec3 toCentre = new Vec3(cx - player.getX(), 0, cz - player.getZ());
				targetShift = toCentre.scale(FreeflowConfig.framingCentroidBias);
				if (targetShift.length() > FreeflowConfig.framingCentroidMaxShift) {
					targetShift = targetShift.normalize().scale(FreeflowConfig.framingCentroidMaxShift);
				}
				autoYaw(toCentre);
			}
		}

		double k = 0.12; // per-tick easing
		distanceBonus += (targetDist - distanceBonus) * k;
		heightBonus += (targetHeight - heightBonus) * k;
		fovBonus += (targetFov - fovBonus) * k;
		shift = shift.lerp(targetShift, k);
	}

	private static void autoYaw(Vec3 toCentre) {
		if (ClientCombo.clientTick - lastMouseTick < FreeflowConfig.framingAutoYawDelayTicks
				|| toCentre.lengthSqr() < 1.0) {
			return;
		}
		float centreYaw = (float) Math.toDegrees(Math.atan2(-toCentre.x, toCentre.z));
		float off = Mth.wrapDegrees(centreYaw - FreeflowCamera.yaw());
		float excess = Math.abs(off) - (float) FreeflowConfig.framingAutoYawThreshold;
		if (excess > 0) {
			FreeflowCamera.addYaw(Math.signum(off) * Math.min(FreeflowConfig.framingAutoYawSpeed, excess));
		}
	}

	public static void reset() {
		distanceBonus = heightBonus = fovBonus = 0;
		shift = Vec3.ZERO;
		enemies = 0;
	}
}

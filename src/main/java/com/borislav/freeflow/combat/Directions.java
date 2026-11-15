package com.borislav.freeflow.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Yaw math shared by client and server. Minecraft yaw 0 faces +Z; forward = (-sin, cos). */
public final class Directions {
	private Directions() {}

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

	/** Yaw that makes {@code from} face {@code to} horizontally. */
	public static float yawTo(Entity from, Entity to) {
		return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
	}

	public static double horizontalDistance(Entity a, Entity b) {
		double dx = b.getX() - a.getX();
		double dz = b.getZ() - a.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}

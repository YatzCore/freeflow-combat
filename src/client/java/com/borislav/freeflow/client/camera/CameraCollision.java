package com.borislav.freeflow.client.camera;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Keeps the camera out of blocks by ray casting from the pivot to the desired position. */
public final class CameraCollision {
	private CameraCollision() {}

	/** Half-size of the camera "box" (same idea as vanilla's third-person zoom check). */
	private static final double RADIUS = 0.1;
	/** Gap kept between the camera and the wall it hit. */
	private static final double MARGIN = 0.05;
	private static final double MIN_DISTANCE = 0.3;

	/** Longest distance (along pivot -> desired) the camera can move without entering a block. */
	public static double maxDistance(BlockGetter level, Entity entity, Vec3 pivot, Vec3 desired) {
		double length = desired.distanceTo(pivot);
		double best = length;

		for (int i = 0; i < 8; i++) {
			double ox = ((i & 1) * 2 - 1) * RADIUS;
			double oy = ((i >> 1 & 1) * 2 - 1) * RADIUS;
			double oz = ((i >> 2 & 1) * 2 - 1) * RADIUS;
			Vec3 from = pivot.add(ox, oy, oz);
			Vec3 to = desired.add(ox, oy, oz);

			HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
			if (hit.getType() != HitResult.Type.MISS) {
				best = Math.min(best, hit.getLocation().distanceTo(from) - MARGIN);
			}
		}
		return Math.max(MIN_DISTANCE, Math.min(best, length));
	}
}

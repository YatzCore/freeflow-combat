package com.borislav.freeflow.ai;

import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What has been done to an enemy (stunned, knocked down, beaten), shared by every player: in co-op
 * a zombie one player stunned can't attack the other, and one player can finish (beatdown, ground
 * takedown) an enemy the other put down. Turn-taking stays per player ({@link EnemySlot}).
 * Server thread only.
 */
public final class MobStatus {
	private static final Map<Mob, MobStatus> ALL = new WeakHashMap<>();

	/** Enemy is stunned (countered / stunned / beaten down) until this tick: it can't attack. */
	public long stunnedUntil = 0;
	/** Enemy is knocked down until this tick (ground takedown possible). */
	public long knockedDownUntil = 0;
	/** Body yaw a knocked-down enemy lies at (kept fixed so it doesn't swivel on the ground). */
	public float downYaw = Float.NaN;
	/** Consecutive beatdown hits on this enemy. */
	public int beatdownHits = 0;

	public static MobStatus of(Mob mob) {
		return ALL.computeIfAbsent(mob, m -> new MobStatus());
	}
}

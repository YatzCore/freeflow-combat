package com.borislav.freeflow.ai;

import net.minecraft.world.entity.Mob;

/** Director bookkeeping for one enemy fighting one player. */
public class EnemySlot {
	public final Mob mob;
	/** Game tick the telegraphed attack lands, or -1 if not attacking. */
	public long attackAt = -1;
	/** Earliest tick this enemy may start another attack. */
	public long nextAllowed = 0;
	/** Enemy is stunned (countered) until this tick. */
	public long stunnedUntil = 0;
	/** Last time a circling move was issued (throttle). */
	public long lastReposition = -100;

	public EnemySlot(Mob mob) {
		this.mob = mob;
	}

	public boolean isTelegraphing() {
		return attackAt >= 0;
	}

	public boolean isStunned(long now) {
		return now < stunnedUntil;
	}
}

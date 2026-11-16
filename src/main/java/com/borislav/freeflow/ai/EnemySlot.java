package com.borislav.freeflow.ai;

import net.minecraft.world.entity.Mob;

/** Director bookkeeping for one enemy fighting one player. */
public class EnemySlot {
	public final Mob mob;
	/** Game tick the telegraphed attack lands, or -1 if not attacking. */
	public long attackAt = -1;
	/** The pending "attack" is a creeper fuse (it explodes on its own; counter = kick it away). */
	public boolean creeper = false;
	/** The pending attack is a ranged shot. */
	public boolean ranged = false;
	/** Telegraph kind of the pending attack (see {@link EnemyRole#telegraphKind()}). */
	public int kind = 0;
	/** Earliest tick this enemy may start another attack. */
	public long nextAllowed = 0;
	/** Enemy is stunned (countered / stunned / beaten down) until this tick: it can't attack. */
	public long stunnedUntil = 0;
	/** Body yaw a knocked-down enemy lies at (kept fixed so it doesn't swivel on the ground). */
	public float downYaw = Float.NaN;
	/** Enemy is knocked down until this tick (ground takedown possible). */
	public long knockedDownUntil = 0;
	/** Consecutive beatdown hits on this enemy. */
	public int beatdownHits = 0;
	/** Last time a circling move was issued (throttle). */
	public long lastReposition = -100;

	public EnemySlot(Mob mob) {
		this.mob = mob;
	}

	public boolean isTelegraphing() {
		return attackAt >= 0;
	}

	/** Can't attack (stunned or knocked down). */
	public boolean isStunned(long now) {
		return now < stunnedUntil || now < knockedDownUntil;
	}

	public boolean isKnockedDown(long now) {
		return now < knockedDownUntil;
	}
}

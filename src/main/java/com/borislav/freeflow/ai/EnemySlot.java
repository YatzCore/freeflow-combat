package com.borislav.freeflow.ai;

import net.minecraft.world.entity.Mob;

/** Director bookkeeping for one enemy fighting one player (its turns); what was done to it is {@link MobStatus}. */
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
	/** Stun / knockdown / beatdown state, shared with every other player fighting this enemy. */
	public final MobStatus status;
	/** Last time a circling move was issued (throttle). */
	public long lastReposition = -100;
	/** Until this tick the enemy may leave the ring and close in to attack; -1 = waiting. */
	public long approachUntil = -1;

	public boolean isApproaching(long now) {
		return now < approachUntil && !isTelegraphing();
	}

	public EnemySlot(Mob mob) {
		this.mob = mob;
		this.status = MobStatus.of(mob);
	}

	public boolean isTelegraphing() {
		return attackAt >= 0;
	}

	/** Can't attack (stunned or knocked down). */
	public boolean isStunned(long now) {
		return now < status.stunnedUntil || now < status.knockedDownUntil;
	}

	public boolean isKnockedDown(long now) {
		return now < status.knockedDownUntil;
	}
}

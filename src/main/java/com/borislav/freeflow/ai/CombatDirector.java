package com.borislav.freeflow.ai;

import com.borislav.freeflow.combat.CombatModeTracker;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.CombatStateManager;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.mixin.CreeperAccessor;
import com.borislav.freeflow.network.TelegraphPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;

import java.util.Iterator;

/**
 * Arkham-style enemy turn-taking. Enemies fighting a freeflow player don't attack on
 * their own: they ask the director (melee via MeleeAttackGoalMixin / MobMixin, ranged
 * via the ranged goal mixins). The director caps simultaneous melee attacks and shots,
 * spaces their starts apart, and announces each with a telegraph ({@link TelegraphPayload})
 * whose colour depends on the enemy's {@link EnemyRole}. Melee enemies without a turn
 * circle at a distance.
 */
public final class CombatDirector {
	private CombatDirector() {}

	/** Set while the director itself performs an attack, so the attack hooks let it through. */
	public static boolean performing = false;

	/** True when this player's fights are run by the director (freeflow active). */
	public static boolean isDirected(ServerPlayer player) {
		CombatState state = CombatStateManager.peek(player);
		return state != null && CombatModeTracker.isFreeflowActive(player, state);
	}

	public static EnemySlot slot(CombatState state, Mob mob) {
		EnemySlot slot = state.enemies.get(mob.getId());
		if (slot == null || slot.mob != mob) {
			slot = new EnemySlot(mob);
			state.enemies.put(mob.getId(), slot);
		}
		return slot;
	}

	public static int activeAttacks(CombatState state) {
		int n = 0;
		for (EnemySlot s : state.enemies.values()) {
			if (s.isTelegraphing()) {
				n++;
			}
		}
		return n;
	}

	private static int active(CombatState state, boolean ranged) {
		int n = 0;
		for (EnemySlot s : state.enemies.values()) {
			if (s.isTelegraphing() && s.ranged == ranged && !s.creeper) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A mob in melee range wants to attack. Starts a telegraphed attack if it gets a turn.
	 * Returns true if an attack was started.
	 */
	public static boolean requestAttack(ServerPlayer player, Mob mob) {
		return request(player, mob, false);
	}

	/** A ranged mob wants to shoot. Starts a telegraphed shot if it gets a turn. */
	public static boolean requestRangedAttack(ServerPlayer player, Mob mob) {
		return request(player, mob, true);
	}

	private static boolean request(ServerPlayer player, Mob mob, boolean ranged) {
		CombatState state = CombatStateManager.get(player);
		long now = player.level().getGameTime();
		EnemySlot slot = slot(state, mob);
		if (slot.isTelegraphing() || slot.isStunned(now) || now < slot.nextAllowed || attackingSomeoneElse(player, mob)) {
			return false;
		}
		int cap = ranged ? FreeflowConfig.maxSimultaneousShooters : FreeflowConfig.maxSimultaneousAttackers;
		if (active(state, ranged) >= cap || now - state.lastTelegraphTick < FreeflowConfig.telegraphGapTicks) {
			return false;
		}
		EnemyRole role = EnemyRole.of(mob);
		int ticks = ranged ? FreeflowConfig.rangedTelegraphTicks : FreeflowConfig.telegraphTicks;
		slot.ranged = ranged;
		slot.kind = ranged ? EnemyRole.RANGED.telegraphKind() : role.telegraphKind();
		slot.attackAt = now + ticks;
		state.lastTelegraphTick = now;
		sendTelegraph(player, mob, ticks, slot.kind);
		MobReaction.send(mob, MobReaction.WINDUP, ticks, slot.kind, player);
		return true;
	}

	/** Co-op: one enemy, one attack at a time, even when several players fight it. */
	private static boolean attackingSomeoneElse(ServerPlayer player, Mob mob) {
		for (Player other : player.level().players()) {
			if (other == player || !(other instanceof ServerPlayer sp)) {
				continue;
			}
			CombatState state = CombatStateManager.peek(sp);
			EnemySlot slot = state == null ? null : state.enemies.get(mob.getId());
			if (slot != null && slot.mob == mob && slot.isTelegraphing()) {
				return true;
			}
		}
		return false;
	}

	/** Ticks an enemy given the go-ahead has to reach you and start its attack. */
	static final int APPROACH_TICKS = 60;
	/** Min ticks between two enemies being sent in, so they peel off the ring one by one. */
	static final int APPROACH_GAP_TICKS = 8;

	/**
	 * True if the mob should hold back on the ring instead of closing in. Like the Arkham
	 * thugs, enemies stand off in a loose ring and only a few at a time (up to the attacker
	 * cap, counting those already attacking) are sent in to attack; everyone else waits.
	 */
	public static boolean shouldWait(ServerPlayer player, Mob mob) {
		if (!isDirected(player)) {
			return false;
		}
		CombatState state = CombatStateManager.get(player);
		long now = player.level().getGameTime();
		EnemySlot slot = slot(state, mob);
		if (slot.isTelegraphing() || slot.isApproaching(now)) {
			return false; // its turn: let it close in
		}
		if (slot.isStunned(now) || now < slot.nextAllowed || attackingSomeoneElse(player, mob)) {
			return true;
		}
		if (now - state.lastApproachGrant < APPROACH_GAP_TICKS
				|| active(state, false) + approaching(state, now) >= FreeflowConfig.maxSimultaneousAttackers) {
			return true;
		}
		slot.approachUntil = now + APPROACH_TICKS;
		state.lastApproachGrant = now;
		return false;
	}

	private static int approaching(CombatState state, long now) {
		int n = 0;
		for (EnemySlot s : state.enemies.values()) {
			if (s.isApproaching(now)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A waiting enemy in its fighting stance: facing you at the stand-off distance, side-stepping
	 * around you with pauses, backing off if too close, and keeping its distance from the others.
	 */
	public static void circle(ServerPlayer player, Mob mob) {
		CombatState state = CombatStateManager.get(player);
		long now = player.level().getGameTime();
		EnemySlot slot = slot(state, mob);
		mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
		if (slot.isStunned(now)) {
			mob.getNavigation().stop();
			return;
		}
		double dx = mob.getX() - player.getX();
		double dz = mob.getZ() - player.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		// Each enemy has its own ring a little in or out, so the circle looks loose, not drilled.
		double ring = FreeflowConfig.standoffDistance + (Math.floorMod(mob.getId(), 3) - 1) * 0.6;
		if (dist > ring + 3.0 || Math.abs(mob.getY() - player.getY()) > 2.0) {
			// Far away (or on another level): walk over to the ring first.
			if (now - slot.lastReposition >= 10) {
				slot.lastReposition = now;
				double angle = Math.atan2(dz, dx);
				mob.getNavigation().moveTo(player.getX() + Math.cos(angle) * ring, player.getY(),
						player.getZ() + Math.sin(angle) * ring, 1.0);
			}
			return;
		}
		mob.getNavigation().stop();
		// Face the player and strafe relative to that facing (the way skeletons circle).
		mob.lookAt(player, 30.0f, 30.0f);
		float forward = dist < ring - 0.8 ? -0.5f : dist > ring + 0.8 ? 0.5f : 0.0f;
		// Side-step one way for a while, pause in stance, then switch direction.
		long phase = (now + mob.getId() * 37L) % 120;
		float side = phase < 30 ? 0.0f : ((now + mob.getId() * 37L) / 120 + mob.getId()) % 2 == 0 ? 0.35f : -0.35f;
		// Keep away from the other waiting enemies: step away from the nearest one too close.
		double yaw = Math.toRadians(mob.getYRot());
		double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw); // the mob's right-hand side
		double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
		double spacing = FreeflowConfig.enemySpacing;
		Mob nearest = null;
		double nearestD = spacing;
		for (EnemySlot other : state.enemies.values()) {
			Mob o = other.mob;
			if (o == mob || !o.isAlive()) {
				continue;
			}
			double d = o.distanceTo(mob);
			if (d < nearestD) {
				nearestD = d;
				nearest = o;
			}
		}
		if (nearest != null) {
			double ox = nearest.getX() - mob.getX(), oz = nearest.getZ() - mob.getZ();
			double push = 0.55 * (1.0 - nearestD / spacing) + 0.25;
			// strafe()'s second argument moves the mob to its LEFT when positive (it becomes xxa).
			side = (float) (ox * rightX + oz * rightZ > 0 ? push : -push);
			if (ox * fwdX + oz * fwdZ > 0 && forward >= 0) {
				forward = -0.3f; // someone's in front: step back out of the way
			}
		}
		mob.getMoveControl().strafe(forward, side);
	}

	/** Resolves due attacks and forgets dead/invalid enemies. */
	public static void tick(ServerPlayer player, CombatState state) {
		long now = player.level().getGameTime();
		watchCreepers(player, state, now);
		Iterator<EnemySlot> it = state.enemies.values().iterator();
		while (it.hasNext()) {
			EnemySlot slot = it.next();
			Mob mob = slot.mob;
			if (!mob.isAlive() || mob.isRemoved() || mob.level() != player.level()) {
				if (slot.isTelegraphing()) {
					sendTelegraph(player, mob, 0, 0);
				}
				it.remove();
				continue;
			}
			if (slot.isKnockedDown(now) && !Float.isNaN(slot.status.downYaw)) {
				// Lying on the ground: don't let the AI swivel the body around.
				mob.setYRot(slot.status.downYaw);
				mob.setYBodyRot(slot.status.downYaw);
				mob.setYHeadRot(slot.status.downYaw);
			}
			if (slot.isTelegraphing() && now >= slot.attackAt) {
				slot.attackAt = -1;
				sendTelegraph(player, mob, 0, 0);
				if (slot.creeper) {
					slot.creeper = false; // the creeper explodes by itself
				} else if (slot.ranged) {
					slot.nextAllowed = now + Mth.nextInt(mob.getRandom(),
							FreeflowConfig.rangedCooldownMin, FreeflowConfig.rangedCooldownMax);
					shoot(player, mob);
				} else {
					slot.nextAllowed = now + Mth.nextInt(mob.getRandom(),
							FreeflowConfig.enemyAttackCooldownMin, FreeflowConfig.enemyAttackCooldownMax);
					slot.approachUntil = -1; // done: back to the ring
					strike(player, mob);
				}
			}
		}
	}

	/**
	 * Creepers don't take turns, they explode. When one near the player starts its fuse, the
	 * fuse becomes an orange telegraph (counter = kick it away); if the fuse stops, so does it.
	 */
	private static void watchCreepers(ServerPlayer player, CombatState state, long now) {
		for (Creeper creeper : player.level().getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(8.0),
				c -> c.isAlive() && c.getTarget() == player)) {
			EnemySlot slot = slot(state, creeper);
			CreeperAccessor fuse = (CreeperAccessor) creeper;
			if (creeper.getSwellDir() > 0 && !slot.isTelegraphing()) {
				int left = Math.max(1, fuse.freeflow$getMaxSwell() - fuse.freeflow$getSwell());
				slot.creeper = true;
				slot.ranged = false;
				slot.kind = TelegraphPayload.CREEPER;
				slot.attackAt = now + left;
				sendTelegraph(player, creeper, left, TelegraphPayload.CREEPER);
				MobReaction.send(creeper, MobReaction.WINDUP, left, TelegraphPayload.CREEPER, player);
			} else if (creeper.getSwellDir() <= 0 && slot.isTelegraphing() && slot.creeper) {
				slot.attackAt = -1;
				slot.creeper = false;
				sendTelegraph(player, creeper, 0, 0);
			}
		}
	}

	private static void strike(ServerPlayer player, Mob mob) {
		MobReaction.send(mob, MobReaction.ATTACK, 6, player); // the swing plays even if it misses
		if (!mob.isWithinMeleeAttackRange(player) || !mob.getSensing().hasLineOfSight(player)) {
			return; // the player got away
		}
		mob.swing(InteractionHand.MAIN_HAND);
		performing = true;
		try {
			mob.doHurtTarget(player); // may be blocked by lunge/evade i-frames
		} finally {
			performing = false;
		}
	}

	private static void shoot(ServerPlayer player, Mob mob) {
		if (!(mob instanceof RangedAttackMob shooter) || !mob.getSensing().hasLineOfSight(player)) {
			return;
		}
		mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
		performing = true;
		try {
			shooter.performRangedAttack(player, 1.0f);
		} finally {
			performing = false;
		}
	}

	/** Cancels every pending attack (freeflow ended). */
	public static void clear(ServerPlayer player, CombatState state) {
		if (state.enemies.isEmpty()) {
			return;
		}
		for (EnemySlot slot : state.enemies.values()) {
			if (slot.isTelegraphing()) {
				sendTelegraph(player, slot.mob, 0, 0);
			}
		}
		state.enemies.clear();
	}

	public static void sendTelegraph(ServerPlayer player, Mob mob, int ticks) {
		sendTelegraph(player, mob, ticks, 0);
	}

	public static void sendTelegraph(ServerPlayer player, Mob mob, int ticks, int kind) {
		if (ServerPlayNetworking.canSend(player, TelegraphPayload.TYPE)) {
			ServerPlayNetworking.send(player, new TelegraphPayload(mob.getId(), ticks, kind));
		}
	}
}

package com.borislav.freeflow.ai;

import com.borislav.freeflow.combat.CombatModeTracker;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.CombatStateManager;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.TelegraphPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

import java.util.Iterator;

/**
 * Arkham-style enemy turn-taking. Melee mobs fighting a freeflow player don't attack
 * on their own: they ask the director (via MeleeAttackGoalMixin). The director grants
 * at most {@link FreeflowConfig#maxSimultaneousAttackers} attacks at once, spaces their
 * starts apart, and announces each with a telegraph ({@link TelegraphPayload}) that the
 * player can counter. Enemies without a turn circle at a distance.
 */
public final class CombatDirector {
	private CombatDirector() {}

	/** True when this player's fights are run by the director (freeflow active). */
	public static boolean isDirected(ServerPlayer player) {
		CombatState state = CombatStateManager.peek(player);
		return state != null && CombatModeTracker.isFreeflowActive(player, state);
	}

	private static EnemySlot slot(CombatState state, Mob mob) {
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

	/**
	 * A mob in melee range wants to attack. Starts a telegraphed attack if it gets a turn.
	 * Returns true if an attack was started.
	 */
	public static boolean requestAttack(ServerPlayer player, Mob mob) {
		CombatState state = CombatStateManager.get(player);
		long now = player.level().getGameTime();
		EnemySlot slot = slot(state, mob);
		if (slot.isTelegraphing() || slot.isStunned(now) || now < slot.nextAllowed) {
			return false;
		}
		if (activeAttacks(state) >= FreeflowConfig.maxSimultaneousAttackers
				|| now - state.lastTelegraphTick < FreeflowConfig.telegraphGapTicks) {
			return false;
		}
		slot.attackAt = now + FreeflowConfig.telegraphTicks;
		state.lastTelegraphTick = now;
		sendTelegraph(player, mob, FreeflowConfig.telegraphTicks);
		return true;
	}

	/** True if the mob should hold back (no turn) instead of closing in. */
	public static boolean shouldWait(ServerPlayer player, Mob mob) {
		if (!isDirected(player)) {
			return false;
		}
		CombatState state = CombatStateManager.get(player);
		long now = player.level().getGameTime();
		EnemySlot slot = slot(state, mob);
		if (slot.isTelegraphing()) {
			return false; // its turn: let it close in
		}
		boolean hasTurnAvailable = !slot.isStunned(now) && now >= slot.nextAllowed
				&& activeAttacks(state) < FreeflowConfig.maxSimultaneousAttackers;
		return !hasTurnAvailable && mob.distanceTo(player) < FreeflowConfig.circleRadius + 1.0;
	}

	/** Keeps a waiting mob on a ring around the player, drifting sideways. */
	public static void circle(ServerPlayer player, Mob mob) {
		CombatState state = CombatStateManager.get(player);
		long now = player.level().getGameTime();
		EnemySlot slot = slot(state, mob);
		mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
		if (now - slot.lastReposition < 10) {
			return;
		}
		slot.lastReposition = now;
		double angle = Math.atan2(mob.getZ() - player.getZ(), mob.getX() - player.getX());
		angle += (mob.getId() % 2 == 0 ? 1 : -1) * Math.toRadians(25);
		double r = FreeflowConfig.circleRadius;
		mob.getNavigation().moveTo(player.getX() + Math.cos(angle) * r, player.getY(),
				player.getZ() + Math.sin(angle) * r, 0.9);
	}

	/** Resolves due attacks and forgets dead/invalid enemies. */
	public static void tick(ServerPlayer player, CombatState state) {
		long now = player.level().getGameTime();
		Iterator<EnemySlot> it = state.enemies.values().iterator();
		while (it.hasNext()) {
			EnemySlot slot = it.next();
			Mob mob = slot.mob;
			if (!mob.isAlive() || mob.isRemoved() || mob.level() != player.level()) {
				if (slot.isTelegraphing()) {
					sendTelegraph(player, mob, 0);
				}
				it.remove();
				continue;
			}
			if (slot.isTelegraphing() && now >= slot.attackAt) {
				slot.attackAt = -1;
				slot.nextAllowed = now + Mth.nextInt(mob.getRandom(),
						FreeflowConfig.enemyAttackCooldownMin, FreeflowConfig.enemyAttackCooldownMax);
				sendTelegraph(player, mob, 0);
				if (mob.isWithinMeleeAttackRange(player) && mob.getSensing().hasLineOfSight(player)) {
					mob.swing(InteractionHand.MAIN_HAND);
					mob.doHurtTarget(player); // may be blocked by lunge i-frames
				}
			}
		}
	}

	/** Cancels every pending attack (freeflow ended). */
	public static void clear(ServerPlayer player, CombatState state) {
		if (state.enemies.isEmpty()) {
			return;
		}
		for (EnemySlot slot : state.enemies.values()) {
			if (slot.isTelegraphing()) {
				sendTelegraph(player, slot.mob, 0);
			}
		}
		state.enemies.clear();
	}

	public static void sendTelegraph(ServerPlayer player, Mob mob, int ticks) {
		if (ServerPlayNetworking.canSend(player, TelegraphPayload.TYPE)) {
			ServerPlayNetworking.send(player, new TelegraphPayload(mob.getId(), ticks));
		}
	}
}

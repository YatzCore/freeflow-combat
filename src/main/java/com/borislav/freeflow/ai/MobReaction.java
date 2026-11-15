package com.borislav.freeflow.ai;

import com.borislav.freeflow.network.MobReactionPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Enemy body language, Arkham style. The server decides what happened to an enemy and
 * broadcasts it; every client that can see the mob animates it (MobAnimator).
 */
public enum MobReaction {
	/** Winding up a telegraphed attack. param = telegraph kind (0 normal, 1 armed, 2 ranged). */
	WINDUP,
	/** The attack swing itself. */
	ATTACK,
	/** Head snap from a light hit. */
	HIT_LIGHT,
	/** Stagger back from a medium hit. */
	HIT_MEDIUM,
	/** Knocked off its feet by a heavy hit: backward roll. */
	LAUNCHED,
	/** Dazed, swaying. */
	STUNNED,
	/** Falls on its back, lies there, gets up at the end. */
	KNOCKED_DOWN,
	/** Knocked out: falls flat and stays down (replaces the vanilla death tip-over). */
	KNOCKED_OUT,
	/** Countered. param = variant (0 block + punch, 1 parry + kick, 2 throw over the shoulder). */
	COUNTERED;

	public static final int COUNTER_STRIKE = 0;
	public static final int COUNTER_KICK = 1;
	public static final int COUNTER_THROW = 2;

	public static MobReaction byId(int id) {
		MobReaction[] v = values();
		return id >= 0 && id < v.length ? v[id] : HIT_LIGHT;
	}

	/** Sends the reaction to every player who can see the mob. */
	public static void send(Mob mob, MobReaction reaction, int ticks, int param, Entity attacker) {
		MobReactionPayload payload = new MobReactionPayload(mob.getId(), reaction, ticks, param,
				attacker != null ? attacker.getId() : -1);
		for (ServerPlayer player : PlayerLookup.tracking(mob)) {
			if (ServerPlayNetworking.canSend(player, MobReactionPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void send(Mob mob, MobReaction reaction, int ticks, Entity attacker) {
		send(mob, reaction, ticks, 0, attacker);
	}
}

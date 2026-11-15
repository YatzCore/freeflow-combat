package com.borislav.freeflow.combat;

import com.borislav.freeflow.network.AnimationPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Combat animation names (defined in assets/freeflow/player_animations/freeflow_combat.json)
 * and a helper that plays one on a player for everyone who can see them.
 */
public final class Anim {
	private Anim() {}

	public static final String STRIKE_RIGHT = "strike_right";
	public static final String STRIKE_LEFT = "strike_left";
	public static final String KICK = "kick";
	public static final String BEATDOWN_RIGHT = "beatdown_right";
	public static final String BEATDOWN_LEFT = "beatdown_left";
	public static final String FINISHER = "finisher";
	public static final String COUNTER = "counter";
	public static final String STUN = "stun";
	public static final String EVADE = "evade";
	public static final String VAULT = "vault";
	public static final String GROUND_TAKEDOWN = "ground_takedown";
	public static final String SPECIAL_TAKEDOWN = "special_takedown";

	public static final List<String> ALL = List.of(STRIKE_RIGHT, STRIKE_LEFT, KICK, BEATDOWN_RIGHT, BEATDOWN_LEFT,
			FINISHER, COUNTER, STUN, EVADE, VAULT, GROUND_TAKEDOWN, SPECIAL_TAKEDOWN);

	/** Strike variety: right, left, right, kick, repeat. */
	public static String strike(int index) {
		return switch (index % 4) {
			case 0 -> STRIKE_RIGHT;
			case 1 -> STRIKE_LEFT;
			case 2 -> STRIKE_RIGHT;
			default -> KICK;
		};
	}

	public static String beatdown(int index) {
		return index % 2 == 0 ? BEATDOWN_RIGHT : BEATDOWN_LEFT;
	}

	/** Plays the animation on {@code player} for the player and everyone tracking them. */
	public static void play(ServerPlayer player, String name) {
		AnimationPayload payload = new AnimationPayload(player.getId(), name);
		if (ServerPlayNetworking.canSend(player, AnimationPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player && ServerPlayNetworking.canSend(other, AnimationPayload.TYPE)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}

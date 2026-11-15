package com.borislav.freeflow.combat;

import com.borislav.freeflow.ai.CombatDirector;
import com.borislav.freeflow.combat.moves.GroundTakedownMove;
import com.borislav.freeflow.combat.moves.Move;
import com.borislav.freeflow.combat.moves.MoveRegistry;
import com.borislav.freeflow.network.CombatInputPayload;
import com.borislav.freeflow.network.CombatSyncPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns every player's {@link CombatState} on the server, ticks it and dispatches inputs to moves. */
public final class CombatStateManager {
	private static final Map<UUID, CombatState> STATES = new HashMap<>();

	private CombatStateManager() {}

	public static void init() {
		MoveRegistry.init();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> get(handler.getPlayer()).dirty = true);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(CombatInputPayload.TYPE,
				(payload, context) -> handleInput(context.player(), payload));

		// Lunge i-frames.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				CombatState state = STATES.get(player.getUUID());
				return state == null || player.level().getGameTime() >= state.invulnerableUntil;
			}
			return true;
		});

		// Getting hit breaks the combo.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0f && !blocked) {
				CombatState state = STATES.get(player.getUUID());
				if (state != null) {
					ComboTracker.breakCombo(player, state);
					GroundTakedownMove.interrupt(player, state);
				}
			}
		});
	}

	public static CombatState get(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new CombatState());
	}

	/** State if it exists, without creating one (cheap check for mob AI hooks). */
	@Nullable
	public static CombatState peek(ServerPlayer player) {
		return STATES.get(player.getUUID());
	}

	private static void tickPlayer(ServerPlayer player) {
		CombatState state = get(player);
		CombatModeTracker.tick(player, state);

		if (CombatModeTracker.isFreeflowActive(player, state)) {
			for (Move move : MoveRegistry.all()) {
				move.tick(player, state);
			}
			CombatDirector.tick(player, state);
		} else {
			state.clearMoves();
			CombatDirector.clear(player, state);
			ComboTracker.breakCombo(player, state);
			if (!state.inCombat) {
				state.maxCombo = 0; // fight over
			}
		}

		if (state.dirty) {
			sync(player, state);
		}
	}

	/** Entry point for a combat input (from the network, or directly from tests). */
	public static void handleInput(ServerPlayer player, CombatInputPayload payload) {
		CombatState state = get(player);
		CombatAction action = payload.action();

		if (action == CombatAction.TOGGLE_MODE) {
			state.mode = state.mode.next();
			state.calmTicks = 0;
			CombatModeTracker.tick(player, state);
		} else if (action.requiresFreeflow()) {
			if (!CombatModeTracker.isFreeflowActive(player, state)) {
				return; // stale/forged input: ignore
			}
			long now = player.level().getGameTime();
			if (now < state.staggerUntil || now < state.lockedUntil) {
				return; // staggered or mid-takedown: every move is locked out
			}
			Move move = MoveRegistry.get(action);
			if (move != null && !move.execute(player, state, payload)) {
				return;
			}
		} else {
			return;
		}

		state.lastAction = action;
		state.acceptedInputs++;
		state.dirty = true;
	}

	private static void sync(ServerPlayer player, CombatState state) {
		if (ServerPlayNetworking.canSend(player, CombatSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CombatSyncPayload(state.mode, state.inCombat, state.engagedCount,
					state.lastAction, state.acceptedInputs));
		}
		state.dirty = false;
	}
}

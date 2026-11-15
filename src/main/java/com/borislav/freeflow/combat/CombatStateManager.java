package com.borislav.freeflow.combat;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.network.CombatInputPayload;
import com.borislav.freeflow.network.CombatSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns every player's {@link CombatState} on the server and ticks it. */
public final class CombatStateManager {
	private static final Map<UUID, CombatState> STATES = new HashMap<>();

	private CombatStateManager() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				CombatState state = get(player);
				CombatModeTracker.tick(player, state);
				if (state.dirty) {
					sync(player, state);
				}
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> get(handler.getPlayer()).dirty = true);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(CombatInputPayload.TYPE,
				(payload, context) -> handleInput(context.player(), payload));
	}

	public static CombatState get(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new CombatState());
	}

	private static void handleInput(ServerPlayer player, CombatInputPayload payload) {
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
			// Moves are executed here from milestone 4 on (MoveRegistry).
			FreeflowMod.LOGGER.debug("{} -> {} (fwd={}, strafe={}, aimYaw={})", player.getName().getString(), action,
					payload.forward(), payload.strafe(), payload.aimYaw());
		} else {
			return;
		}

		state.lastAction = action;
		state.acceptedInputs++;
		state.dirty = true;
	}

	private static void sync(ServerPlayer player, CombatState state) {
		ServerPlayNetworking.send(player, new CombatSyncPayload(state.mode, state.inCombat, state.engagedCount,
				state.lastAction, state.acceptedInputs));
		state.dirty = false;
	}
}

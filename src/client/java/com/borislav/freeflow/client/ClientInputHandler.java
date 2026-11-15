package com.borislav.freeflow.client;

import com.borislav.freeflow.client.hud.DebugOverlay;
import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.network.CombatInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Turns key presses into combat inputs sent to the server. */
public final class ClientInputHandler {
	private ClientInputHandler() {}

	public static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}

		while (Keybinds.debugOverlay.consumeClick()) {
			DebugOverlay.enabled = !DebugOverlay.enabled;
		}
		while (Keybinds.toggleMode.consumeClick()) {
			send(CombatAction.TOGGLE_MODE);
		}

		boolean active = ClientCombatState.isFreeflowActive();
		pressed(Keybinds.counter, active, CombatAction.COUNTER);
		pressed(Keybinds.evade, active, CombatAction.EVADE);
		pressed(Keybinds.stun, active, CombatAction.STUN);
		pressed(Keybinds.groundTakedown, active, CombatAction.GROUND_TAKEDOWN);
		pressed(Keybinds.specialTakedown, active, CombatAction.SPECIAL_TAKEDOWN);
	}

	/** Called from the attack-key mixin instead of the vanilla attack. */
	public static void onStrike() {
		send(CombatAction.STRIKE);
	}

	private static void pressed(KeyMapping key, boolean active, CombatAction action) {
		while (key.consumeClick()) {
			if (active) {
				send(action);
			}
		}
	}

	private static void send(CombatAction action) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !ClientPlayNetworking.canSend(CombatInputPayload.TYPE)) {
			return; // server doesn't have the mod
		}
		ClientPlayNetworking.send(new CombatInputPayload(action, player.input.forwardImpulse, player.input.leftImpulse));
		ClientCombatState.lastSentAction = action;
		ClientCombatState.sentInputs++;
	}
}

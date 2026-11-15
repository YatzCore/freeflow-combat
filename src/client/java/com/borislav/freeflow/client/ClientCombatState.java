package com.borislav.freeflow.client;

import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.combat.CombatMode;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client mirror of the server's combat state for the local player. */
public final class ClientCombatState {
	private ClientCombatState() {}

	public static CombatMode mode = CombatMode.AUTO;
	public static boolean inCombat = false;
	public static int engagedCount = 0;
	public static CombatAction lastAcceptedAction = CombatAction.NONE;
	public static int acceptedInputs = 0;

	/** Client-side bookkeeping for the debug overlay. */
	public static CombatAction lastSentAction = CombatAction.NONE;
	public static int sentInputs = 0;

	public static void apply(CombatSyncPayload sync) {
		mode = sync.mode();
		inCombat = sync.inCombat();
		engagedCount = sync.engagedCount();
		lastAcceptedAction = sync.lastAction();
		acceptedInputs = sync.acceptedInputs();
	}

	public static void reset() {
		mode = CombatMode.AUTO;
		inCombat = false;
		engagedCount = 0;
		lastAcceptedAction = CombatAction.NONE;
		acceptedInputs = 0;
		lastSentAction = CombatAction.NONE;
		sentInputs = 0;
	}

	/** True when vanilla attack/mining should be replaced by freeflow. */
	public static boolean isFreeflowActive() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && !player.isSpectator() && inCombat
				&& (FreeflowConfig.allowNonEmptyHand || player.getMainHandItem().isEmpty());
	}
}

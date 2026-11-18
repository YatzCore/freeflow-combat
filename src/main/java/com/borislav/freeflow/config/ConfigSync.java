package com.borislav.freeflow.config;

import com.borislav.freeflow.network.ConfigSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the settings: loads the file at startup, sends the gameplay values to every player
 * who joins. {@code /freeflow reload} is in FreeflowCommands.
 */
public final class ConfigSync {
	private ConfigSync() {}

	public static void init() {
		ConfigStore.load();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
	}

	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigStore.gameplayJson()));
		}
	}

	/** Re-sends the gameplay values to everyone (after they changed). */
	public static void sendAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}
	}
}

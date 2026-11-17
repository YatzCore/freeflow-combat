package com.borislav.freeflow.config;

import com.borislav.freeflow.network.ConfigSyncPayload;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the settings: loads the file at startup, sends the gameplay values to every player
 * who joins, and adds {@code /freeflow reload} (operators) to re-read the file on a running server.
 */
public final class ConfigSync {
	private ConfigSync() {}

	public static void init() {
		ConfigStore.load();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("freeflow").requires(src -> src.hasPermission(2))
						.then(Commands.literal("reload").executes(ctx -> {
							ConfigStore.load();
							sendAll(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Freeflow settings reloaded"), true);
							return Command.SINGLE_SUCCESS;
						}))));
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

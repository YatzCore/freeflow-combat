package com.borislav.freeflow;

import com.borislav.freeflow.arena.Arena;
import com.borislav.freeflow.config.ConfigStore;
import com.borislav.freeflow.config.ConfigSync;
import com.borislav.freeflow.config.FreeflowConfig;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /freeflow reload} (operators): re-read config/freeflow.json and send it to everyone.
 * {@code /freeflow arena start|stop}: the combat challenge (operators, the owner of a singleplayer
 * world, or everyone if the server allows it in the settings).
 */
public final class FreeflowCommands {
	private FreeflowCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("freeflow")
						.then(Commands.literal("reload").requires(src -> src.hasPermission(2)).executes(ctx -> {
							ConfigStore.load();
							ConfigSync.sendAll(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Freeflow settings reloaded"), true);
							return Command.SINGLE_SUCCESS;
						}))
						.then(Commands.literal("arena").requires(FreeflowCommands::mayUseArena)
								.then(Commands.literal("start").executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Component error = Arena.start(player);
									if (error != null) {
										ctx.getSource().sendFailure(error);
										return 0;
									}
									return Command.SINGLE_SUCCESS;
								}))
								.then(Commands.literal("stop").executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									if (!Arena.stop(player)) {
										ctx.getSource().sendFailure(Component.literal("You're not in an arena."));
										return 0;
									}
									ctx.getSource().sendSuccess(() -> Component.literal("Combat challenge ended"), false);
									return Command.SINGLE_SUCCESS;
								})))));
	}

	private static boolean mayUseArena(CommandSourceStack src) {
		if (src.hasPermission(2) || FreeflowConfig.arenaForEveryone) {
			return true;
		}
		ServerPlayer player = src.getPlayer();
		return player != null && src.getServer().isSingleplayerOwner(player.getGameProfile());
	}
}

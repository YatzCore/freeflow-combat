package com.borislav.freeflow;

import com.borislav.freeflow.arena.Arena;
import com.borislav.freeflow.arena.ArenaRecords;
import net.minecraft.ChatFormatting;
import java.util.List;
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
 * {@code /freeflow arena start [classic|infinite] | infinite | stop | records}: the combat challenge
 * (operators, the owner of a singleplayer world, or everyone if the server allows it in the settings).
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
								.then(Commands.literal("start")
										.executes(ctx -> start(ctx.getSource(), Arena.Mode.CLASSIC))
										.then(Commands.literal("classic").executes(ctx -> start(ctx.getSource(), Arena.Mode.CLASSIC)))
										.then(Commands.literal("infinite").executes(ctx -> start(ctx.getSource(), Arena.Mode.INFINITE))))
								.then(Commands.literal("infinite").executes(ctx -> start(ctx.getSource(), Arena.Mode.INFINITE)))
								.then(Commands.literal("records").executes(ctx -> records(ctx.getSource())))
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

	private static int start(CommandSourceStack src, Arena.Mode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Component error = Arena.start(src.getPlayerOrException(), mode);
		if (error != null) {
			src.sendFailure(error);
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Your best results, and the world's best infinite runs. */
	private static int records(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		ArenaRecords records = ArenaRecords.get(src.getServer());
		ArenaRecords.Entry mine = records.of(player.getUUID());
		src.sendSuccess(() -> Component.literal("Combat challenge records").withStyle(ChatFormatting.GOLD), false);
		src.sendSuccess(() -> Component.literal("  Classic, best time: "
				+ (mine.classicSeconds > 0 ? ArenaRecords.time(mine.classicSeconds) : "not won yet")), false);
		src.sendSuccess(() -> Component.literal("  Infinite, most waves: "
				+ (mine.infiniteWaves > 0 ? mine.infiniteWaves + " (" + mine.infiniteKos + " knock-outs)" : "none yet")), false);
		List<ArenaRecords.Entry> top = records.topInfinite(5);
		if (top.size() > 1 || (top.size() == 1 && top.get(0) != mine)) {
			src.sendSuccess(() -> Component.literal("Infinite, this world's best:").withStyle(ChatFormatting.GOLD), false);
			for (int i = 0; i < top.size(); i++) {
				ArenaRecords.Entry e = top.get(i);
				int rank = i + 1;
				src.sendSuccess(() -> Component.literal("  " + rank + ". " + e.name + ": " + e.infiniteWaves + " waves"), false);
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	private static boolean mayUseArena(CommandSourceStack src) {
		if (src.hasPermission(2) || FreeflowConfig.arenaForEveryone) {
			return true;
		}
		ServerPlayer player = src.getPlayer();
		return player != null && src.getServer().isSingleplayerOwner(player.getGameProfile());
	}
}

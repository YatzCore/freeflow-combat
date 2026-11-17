package com.borislav.freeflow.client.dev;

import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.ai.MobReaction;
import com.borislav.freeflow.client.anim.CombatAnimations;
import com.borislav.freeflow.client.anim.MobAnimator;
import com.borislav.freeflow.network.MobReactionPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Developer tool: automatic animation preview. Start the client with environment variable
 * FREEFLOW_PREVIEW=1 (and quick-play into a world). It builds a small stone platform high in
 * the sky, plays each animation on the player (and reaction animations on a zombie) in front
 * of a fixed camera, and writes one contact sheet per animation (frames left to right) to
 * run/screenshots/freeflow_preview/. Then it closes the game.
 *
 * <pre>FREEFLOW_PREVIEW=1 ./gradlew runClient --args="--quickPlaySingleplayer \"New World (1)\""</pre>
 *
 * (tools/preview.py draws the same clips without the game, for quick pose work.)
 */
public final class AnimationPreview {
	private AnimationPreview() {}

	public static final boolean ENABLED = "1".equals(System.getenv("FREEFLOW_PREVIEW"));

	private static final double PX = 0.5;
	private static final double PY = 201.0;
	private static final double PZ = 0.5;

	/** Height of the local player's lowest point above the floor (blocks) as last measured while drawing. */
	public static float lowest = Float.NaN;

	/** Fixed preview camera, read by CameraMixin while the preview runs. */
	@Nullable
	public static Vec3 cameraPos;
	public static float cameraYaw;
	public static float cameraPitch;

	/** Runs {@code action}, then waits {@code delay} ticks before the next step. */
	private record Step(int delay, Runnable action) {}

	private static final Deque<Step> STEPS = new ArrayDeque<>();
	private static int waitTicks = 0;
	private static int ticksInWorld = 0;
	private static boolean started = false;

	private static final List<NativeImage> frames = new ArrayList<>();
	private static String sheetName = "";

	/** Player animations to preview, with the frame times (ticks) to capture. */
	private static final String[] PLAYER_ANIMS = {
			"stance", "shuffle", "advance", "run", "jab_left", "jab_right", "hook_right", "hook_left", "elbow", "knee",
			"front_kick", "side_kick", "spin_backfist", "haymaker", "flying_kick", "flying_punch", "beatdown_right",
			"finisher", "counter", "counter_kick", "counter_throw", "stun", "roll", "evade", "vault", "slide",
			"ground_takedown", "special_takedown", "throw_quick", "throw_aim", "throw_release", "claw_fire",
			"claw_hold", "claw_pull", "claw_slam"};
	private static final int[] FRAMES = {1, 2, 4, 6, 8, 11};

	/** FREEFLOW_PREVIEW_ANIMS=a,b,c overrides the list; FREEFLOW_PREVIEW_FRAMES=1,4,8 the frame ticks;
	 *  FREEFLOW_PREVIEW_ONLY_PLAYER=1 skips the enemy-reaction scenes. */
	private static String[] anims() {
		String env = System.getenv("FREEFLOW_PREVIEW_ANIMS");
		return env != null && !env.isBlank() ? env.split(",") : PLAYER_ANIMS;
	}

	private static int[] frameTicks() {
		String env = System.getenv("FREEFLOW_PREVIEW_FRAMES");
		if (env == null || env.isBlank()) {
			return FRAMES;
		}
		String[] parts = env.split(",");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = Integer.parseInt(parts[i].trim());
		}
		return out;
	}

	public static void tick(Minecraft mc) {
		if (!ENABLED || mc.player == null || mc.level == null) {
			return;
		}
		if (!started) {
			if (++ticksInWorld < 60) {
				return; // let the world settle
			}
			started = true;
			buildScript(mc);
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		Step step = STEPS.poll();
		if (step == null) {
			return;
		}
		step.action().run();
		waitTicks = step.delay();
	}

	private static void then(int delay, Runnable action) {
		STEPS.add(new Step(delay, action));
	}

	private static void buildScript(Minecraft mc) {
		if ("1".equals(System.getenv("FREEFLOW_PREVIEW_SETTINGS"))) {
			// Settings screen: one screenshot per tab, then quit.
			for (com.borislav.freeflow.config.Setting.Category tab : com.borislav.freeflow.config.Setting.Category.values()) {
				then(15, () -> {
					mc.setScreen(com.borislav.freeflow.client.config.SettingsScreen.create(null, tab));
					beginSheet("settings_" + tab.name().toLowerCase(java.util.Locale.ROOT));
				});
				then(1, () -> capture(mc));
				then(1, () -> endSheet(mc));
			}
			then(5, () -> mc.stop());
			return;
		}
		if ("1".equals(System.getenv("FREEFLOW_PREVIEW_MP"))) {
			multiplayerFight(mc);
			return;
		}
		FreeflowMod.LOGGER.info("[preview] starting animation preview");
		mc.options.hideGui = true;
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		command(mc, "gamemode creative @p");
		command(mc, "time set noon");
		command(mc, "weather clear");
		command(mc, "fill -6 200 -6 6 200 6 minecraft:smooth_stone");
		command(mc, "fill -6 201 -6 6 206 6 minecraft:air");
		command(mc, "tp @p " + PX + " " + PY + " " + PZ + " 0 0");
		command(mc, "kill @e[type=minecraft:zombie]"); // leftovers from earlier runs (dev world only)
		double camDist = System.getenv("FREEFLOW_PREVIEW_CLOSE") != null ? 2.6 : 4.2;
		then(20, () -> lookAt(new Vec3(PX + camDist, PY + 1.1, PZ + 0.6), new Vec3(PX, PY + 0.9, PZ)));

		// --- player animations, side view ---
		for (String anim : anims()) {
			then(1, () -> {
				facePlayer(mc, 0f);
				CombatAnimations.playLocal(anim);
				beginSheet("player_" + anim);
			});
			int last = 0;
			for (int f : frameTicks()) {
				int wait = f - last;
				last = f;
				then(Math.max(0, wait - 1), () -> { });
				then(0, () -> capture(mc));
			}
			then(12, () -> endSheet(mc));
		}

		if ("1".equals(System.getenv("FREEFLOW_PREVIEW_ONLY_PLAYER"))) {
			then(10, () -> mc.stop());
			return;
		}

		// --- enemy reactions on a zombie standing in front of the player, side view ---
		then(5, () -> command(mc, "summon minecraft:zombie " + PX + " " + PY + " " + (PZ + 2.0)
				+ " {NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"));
		then(10, () -> lookAt(new Vec3(PX + 5.0, PY + 1.2, PZ + 1.0), new Vec3(PX, PY + 0.8, PZ + 1.4)));
		reaction(mc, MobReaction.HIT_LIGHT, 7, 0, new int[] {1, 3, 6});
		reaction(mc, MobReaction.HIT_MEDIUM, 10, 0, new int[] {1, 4, 8});
		reaction(mc, MobReaction.WINDUP, 12, 0, new int[] {3, 8, 12});
		reaction(mc, MobReaction.ATTACK, 6, 0, new int[] {1, 2, 5});
		reaction(mc, MobReaction.LAUNCHED, 16, 0, new int[] {2, 5, 8, 11, 15});
		reaction(mc, MobReaction.KNOCKED_DOWN, 50, 0, new int[] {2, 5, 8, 14, 37, 41, 45, 49});
		reaction(mc, MobReaction.COUNTERED, 40, MobReaction.COUNTER_THROW, new int[] {1, 4, 6, 9, 12, 20});
		reaction(mc, MobReaction.COUNTERED, 30, MobReaction.COUNTER_STRIKE, new int[] {2, 5, 8, 14, 24});
		reaction(mc, MobReaction.STUNNED, 30, 0, new int[] {5, 15, 25});
		reaction(mc, MobReaction.KNOCKED_OUT, 30, 0, new int[] {1, 4, 7, 12, 25});
		then(30, () -> command(mc, "kill @e[type=minecraft:zombie]"));

		// --- in a fight: guard stance, hit-aligned strikes out of it and back into it ---
		then(1, () -> {
			CombatAnimations.previewFighting = true;
			lookAt(new Vec3(PX + 2.8, PY + 1.1, PZ + 0.6), new Vec3(PX, PY + 0.9, PZ));
		});
		then(14, () -> { });
		scene(mc, "fight_stance_cross", () -> CombatAnimations.playLocal("jab_right", 1), new int[] {0, 1, 2, 4, 8, 12, 18});
		scene(mc, "fight_spin_backfist", () -> CombatAnimations.playLocal("spin_backfist", 1), new int[] {1, 2, 3, 5, 7, 9, 14});
		scene(mc, "fight_flying_punch", () -> CombatAnimations.playLocal("flying_punch", 4), new int[] {1, 2, 3, 4, 5, 8, 12});

		// --- paired counters: the enemy winds up, the counter and its reaction start together ---
		// (it holds a sword and wears a chestplate: what a hand holds must follow a bent forearm, and armour must bend)
		then(5, () -> command(mc, "summon minecraft:zombie " + PX + " " + PY + " " + (PZ + 1.7)
				+ " {NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b,HandItems:[{id:\"minecraft:iron_sword\",count:1},{}],"
				+ "ArmorItems:[{},{},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:leather_helmet\",count:1}]}"));
		then(10, () -> lookAt(new Vec3(PX + 4.2, PY + 1.3, PZ + 0.9), new Vec3(PX, PY + 0.9, PZ + 0.9)));
		pair(mc, "pair_counter", "counter", MobReaction.COUNTER_STRIKE, 30, new int[] {1, 2, 3, 5, 8, 13, 20});
		pair(mc, "pair_counter_kick", "counter_kick", MobReaction.COUNTER_KICK, 30, new int[] {1, 2, 3, 5, 8, 13, 20});
		pair(mc, "pair_counter_throw", "counter_throw", MobReaction.COUNTER_THROW, 40, new int[] {1, 2, 4, 6, 9, 12, 16});

		// --- gadgets: the player's move and the enemy's reaction, started as the gadget code will ---
		gadget(mc, "pair_throw_quick", "throw_quick", 2, MobReaction.GADGET_STRUCK, 30, new int[] {1, 2, 3, 5, 8, 13, 20});
		gadget(mc, "pair_throw_release", "throw_release", 2, MobReaction.GADGET_STRUCK, 30, new int[] {1, 2, 3, 5, 8, 13, 20});
		gadget(mc, "pair_claw_fire", "claw_fire", 2, MobReaction.HOOKED, 20, new int[] {1, 2, 3, 4, 6, 9, 14});
		gadget(mc, "pair_claw_pull", "claw_pull", 2, MobReaction.PULLED, 30, new int[] {1, 2, 4, 6, 9, 13, 18});
		gadget(mc, "pair_claw_slam", "claw_slam", 2, MobReaction.CLOTHESLINED, 45, new int[] {1, 2, 4, 6, 8, 12, 30});

		// --- reaction chains ---
		chain(mc, "chain_dazed_hit", MobReaction.STUNNED, 40, 0, 10, MobReaction.HIT_LIGHT, 7, new int[] {1, 3, 6, 10, 16});
		chain(mc, "chain_armed_swing", MobReaction.WINDUP, 12, 1, 12, MobReaction.ATTACK, 6, new int[] {1, 2, 4, 7, 10});
		chain(mc, "chain_sword_windup", MobReaction.HIT_LIGHT, 7, 0, 12, MobReaction.WINDUP, 12, new int[] {2, 4, 7, 10, 12});
		chain(mc, "chain_sword_swing", MobReaction.WINDUP, 12, 0, 12, MobReaction.ATTACK, 6, new int[] {1, 2, 4, 7, 10});
		chain(mc, "chain_down_then_out", MobReaction.KNOCKED_DOWN, 60, 0, 22, MobReaction.KNOCKED_OUT, 30, new int[] {1, 2, 4, 8, 14});
		then(30, () -> {
			command(mc, "kill @e[type=minecraft:zombie]");
			CombatAnimations.previewFighting = false;
		});

		// --- ground takedown: zombie lying beside the player, three-quarter view ---
		// Zombie faces -X so it lies along +X; its chest ends up right in front of the player.
		then(5, () -> command(mc, "summon minecraft:zombie " + (PX - 0.9) + " " + PY + " " + (PZ + 0.8)
				+ " {NoAI:1b,Rotation:[90f,0f],PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"));
		then(10, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), MobReaction.KNOCKED_DOWN, 200, 0, -1));
			}
			lookAt(new Vec3(PX + 2.1, PY + 1.6, PZ - 1.3), new Vec3(PX - 0.2, PY + 0.5, PZ + 0.8));
		});
		then(15, () -> { });
		then(1, () -> {
			facePlayer(mc, 0f);
			CombatAnimations.playLocal("ground_takedown");
			beginSheet("takedown_ground");
		});
		int last = 0;
		for (int f : new int[] {3, 8, 14, 18, 20, 23, 29}) {
			then(Math.max(0, f - last - 1), () -> { });
			then(0, () -> capture(mc));
			last = f;
		}
		then(10, () -> endSheet(mc));
		then(30, () -> command(mc, "kill @e[type=minecraft:zombie]"));

		then(10, () -> {
			FreeflowMod.LOGGER.info("[preview] done, closing");
			mc.stop();
		});
	}

	/** One sheet: run {@code start}, then capture on the given ticks after it. */
	private static void scene(Minecraft mc, String name, Runnable start, int[] at) {
		then(0, () -> {
			facePlayer(mc, 0f);
			beginSheet(name);
			start.run();
		});
		int last = 0;
		for (int f : at) {
			then(Math.max(0, f - last - 1), () -> { });
			then(0, () -> capture(mc));
			last = f;
		}
		then(16, () -> endSheet(mc));
	}

	/** The zombie winds up, then the player's counter and the zombie's reaction start on the same tick. */
	private static void pair(Minecraft mc, String name, String counter, int variant, int ticks, int[] at) {
		then(12, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), MobReaction.WINDUP, 12, 0, mc.player.getId()));
			}
		});
		scene(mc, name, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), MobReaction.COUNTERED, ticks, variant, mc.player.getId()));
			}
			CombatAnimations.playLocal(counter, 1);
		}, at);
		then(ticks, () -> { });
	}

	/**
	 * A gadget move on the player with its moment {@code lead} ticks away, and the enemy's reaction
	 * sent on that tick (as the gadget code will do it).
	 */
	private static void gadget(Minecraft mc, String name, String move, int lead, MobReaction reaction, int ticks, int[] at) {
		then(8, () -> { });
		then(0, () -> {
			facePlayer(mc, 0f);
			beginSheet(name);
			CombatAnimations.playLocal(move, lead);
		});
		then(lead - 1, () -> { });
		then(0, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), reaction, ticks, 0, mc.player.getId()));
			}
		});
		int last = lead;
		for (int f : at) {
			int tick = f + lead;
			then(Math.max(0, tick - last - 1), () -> { });
			then(0, () -> capture(mc));
			last = tick;
		}
		then(ticks + 8, () -> endSheet(mc));
	}

	/** A reaction, then {@code gap} ticks later a second one on top of it; the sheet shows the second. */
	private static void chain(Minecraft mc, String name, MobReaction first, int firstTicks, int firstParam, int gap,
			MobReaction second, int secondTicks, int[] at) {
		then(gap, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), first, firstTicks, firstParam, mc.player.getId()));
			}
		});
		scene(mc, name, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), second, secondTicks, 0, mc.player.getId()));
			}
		}, at);
		then(firstTicks, () -> { });
	}

	private static void reaction(Minecraft mc, MobReaction reaction, int ticks, int param, int[] at) {
		then(1, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), reaction, ticks, param, mc.player.getId()));
			}
			beginSheet("mob_" + reaction.name().toLowerCase() + (reaction == MobReaction.COUNTERED ? "_" + param : ""));
		});
		int last = 0;
		for (int f : at) {
			then(Math.max(0, f - last - 1), () -> { });
			then(0, () -> capture(mc));
			last = f;
		}
		then(ticks + 6 - last, () -> endSheet(mc));
	}

	@Nullable
	private static Zombie zombie(Minecraft mc) {
		List<Zombie> list = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(8),
				z -> z.isAlive() && !z.isDeadOrDying());
		return list.isEmpty() ? null : list.get(0);
	}

	private static void facePlayer(Minecraft mc, float yaw) {
		mc.player.setYRot(yaw);
		mc.player.setYHeadRot(yaw);
		mc.player.setYBodyRot(yaw);
		mc.player.yRotO = yaw;
		mc.player.yBodyRotO = yaw;
		mc.player.yHeadRotO = yaw;
		mc.player.setXRot(0);
	}

	private static void lookAt(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		cameraPos = from;
		cameraYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		cameraPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
	}

	/**
	 * Multiplayer smoke test (on a dedicated server where this player is an operator): summons
	 * zombies, fights them through the real network path for ~10 s, logs what the server sent back.
	 */
	private static void multiplayerFight(Minecraft mc) {
		FreeflowMod.LOGGER.info("[preview] multiplayer fight");
		String helmet = "{ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}";
		then(5, () -> remote(mc, "gamemode survival"));
		then(5, () -> remote(mc, "effect give @s minecraft:resistance 60 3 true"));
		then(5, () -> remote(mc, "kill @e[type=minecraft:zombie]"));
		for (int i = 0; i < 3; i++) {
			String at = new String[] {"~3 ~ ~", "~-3 ~ ~1", "~ ~ ~3"}[i];
			then(2, () -> remote(mc, "summon minecraft:zombie " + at + " " + helmet));
		}
		int[] maxCombo = {0};
		boolean[] fought = {false};
		for (int t = 0; t < 240; t += 4) {
			int tick = t;
			then(4, () -> {
				if (ClientCombatState.isFreeflowActive()) {
					fought[0] = true;
					com.borislav.freeflow.client.ClientInputHandler.padAction(
							tick % 16 == 0 ? com.borislav.freeflow.combat.CombatAction.COUNTER
									: com.borislav.freeflow.combat.CombatAction.STRIKE);
				}
				maxCombo[0] = Math.max(maxCombo[0], com.borislav.freeflow.client.ClientCombo.combo);
				if (tick % 40 == 0) {
					FreeflowMod.LOGGER.info("[preview] mp t={} freeflow={} sent={} acked={} combo={} health={}", tick,
							ClientCombatState.isFreeflowActive(), ClientCombatState.sentInputs,
							ClientCombatState.acceptedInputs, com.borislav.freeflow.client.ClientCombo.combo,
							mc.player.getHealth());
				}
			});
		}
		then(5, () -> FreeflowMod.LOGGER.info("[preview] mp result: fought={} maxCombo={} sent={} acked={}",
				fought[0], maxCombo[0], ClientCombatState.sentInputs, ClientCombatState.acceptedInputs));
		then(5, () -> remote(mc, "kill @e[type=minecraft:zombie]"));
		then(10, () -> mc.stop());
	}

	private static void remote(Minecraft mc, String cmd) {
		if (mc.player != null) {
			mc.player.connection.sendCommand(cmd);
		}
	}

	private static void command(Minecraft mc, String cmd) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack().withSuppressedOutput()
							.withEntity(server.getPlayerList().getPlayers().get(0)), cmd));
		}
	}

	private static void beginSheet(String name) {
		frames.forEach(NativeImage::close);
		frames.clear();
		sheetName = name;
	}

	private static void capture(Minecraft mc) {
		FreeflowMod.LOGGER.info("[preview] {} frame {}: {}, lowest {} px, joints {}", sheetName, frames.size(),
				CombatAnimations.debugState(), String.format(java.util.Locale.ROOT, "%.2f", lowest * 16f),
				CombatAnimations.debugBends());
		frames.add(Screenshot.takeScreenshot(mc.getMainRenderTarget()));
	}

	/** Writes the captured frames side by side, each scaled to half size. */
	private static void endSheet(Minecraft mc) {
		if (frames.isEmpty()) {
			return;
		}
		int fw = frames.get(0).getWidth() / 2;
		int fh = frames.get(0).getHeight() / 2;
		try (NativeImage sheet = new NativeImage(fw * frames.size(), fh, false)) {
			for (int i = 0; i < frames.size(); i++) {
				NativeImage f = frames.get(i);
				for (int y = 0; y < fh; y++) {
					for (int x = 0; x < fw; x++) {
						sheet.setPixelRGBA(i * fw + x, y, f.getPixelRGBA(x * 2, y * 2));
					}
				}
			}
			File dir = new File(mc.gameDirectory, "screenshots/freeflow_preview");
			dir.mkdirs();
			sheet.writeToFile(new File(dir, sheetName + ".png"));
			FreeflowMod.LOGGER.info("[preview] wrote {}", sheetName);
		} catch (Exception e) {
			FreeflowMod.LOGGER.error("[preview] failed to write " + sheetName, e);
		}
		frames.forEach(NativeImage::close);
		frames.clear();
	}
}

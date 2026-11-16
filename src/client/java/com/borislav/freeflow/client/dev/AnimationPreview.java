package com.borislav.freeflow.client.dev;

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
 */
public final class AnimationPreview {
	private AnimationPreview() {}

	public static final boolean ENABLED = "1".equals(System.getenv("FREEFLOW_PREVIEW"));

	private static final double PX = 0.5;
	private static final double PY = 201.0;
	private static final double PZ = 0.5;

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
			"roll", "evade", "vault", "slide", "run", "ground_takedown", "jab_right", "hook_right", "front_kick", "roundhouse",
			"haymaker", "flying_kick", "finisher", "counter", "counter_kick", "counter_throw", "special_takedown"};
	private static final int[] FRAMES = {1, 3, 5, 7, 10};

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
		then(20, () -> lookAt(new Vec3(PX + 4.2, PY + 1.1, PZ + 0.6), new Vec3(PX, PY + 0.9, PZ)));

		// --- player animations, side view ---
		for (String anim : PLAYER_ANIMS) {
			then(1, () -> {
				facePlayer(mc, 0f);
				CombatAnimations.playLocal(anim);
				beginSheet("player_" + anim);
			});
			int last = 0;
			for (int f : FRAMES) {
				int wait = f - last;
				last = f;
				then(Math.max(0, wait - 1), () -> { });
				then(0, () -> capture(mc));
			}
			then(12, () -> endSheet(mc));
		}

		// --- enemy reactions on a zombie standing in front of the player, side view ---
		then(5, () -> command(mc, "summon minecraft:zombie " + PX + " " + PY + " " + (PZ + 2.0)
				+ " {NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"));
		then(10, () -> lookAt(new Vec3(PX + 5.0, PY + 1.2, PZ + 1.0), new Vec3(PX, PY + 0.8, PZ + 1.4)));
		reaction(mc, MobReaction.HIT_MEDIUM, 10, 0, new int[] {2, 5, 8});
		reaction(mc, MobReaction.WINDUP, 12, 0, new int[] {3, 8, 12});
		reaction(mc, MobReaction.ATTACK, 6, 0, new int[] {1, 3, 5});
		reaction(mc, MobReaction.LAUNCHED, 16, 0, new int[] {2, 5, 8, 11, 15});
		reaction(mc, MobReaction.KNOCKED_DOWN, 30, 0, new int[] {2, 5, 9, 15, 24, 29});
		reaction(mc, MobReaction.COUNTERED, 30, MobReaction.COUNTER_THROW, new int[] {2, 5, 8, 11, 15, 25});
		reaction(mc, MobReaction.STUNNED, 30, 0, new int[] {5, 15, 25});
		then(30, () -> command(mc, "kill @e[type=minecraft:zombie]"));

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
		for (int f : new int[] {2, 4, 6, 9, 12, 16}) {
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

	private static void reaction(Minecraft mc, MobReaction reaction, int ticks, int param, int[] at) {
		then(1, () -> {
			Zombie z = zombie(mc);
			if (z != null) {
				MobAnimator.onReaction(new MobReactionPayload(z.getId(), reaction, ticks, param, mc.player.getId()));
			}
			beginSheet("mob_" + reaction.name().toLowerCase() + (param != 0 ? "_" + param : ""));
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
		FreeflowMod.LOGGER.info("[preview] {} frame {}: {}, pos {}, pose {}", sheetName, frames.size(),
				CombatAnimations.debugState(), mc.player.position(), mc.player.getPose());
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

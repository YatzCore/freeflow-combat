package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.FreeflowMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Enemy animation clips (assets/freeflow/mob_animations.json, written by tools/gen_animations.py).
 * One row per tick; a row is a whole pose:
 * <pre>
 *   [0..2]  whole-body rotation in degrees, applied Rz * Ry * Rx around the hips
 *   [3..4]  whole-body offset x (right), z (back) in blocks of a player-sized mob
 *   [5]     height of the lowest point above the floor, same unit
 *   then for each of head, torso, right arm, left arm, right leg, left leg:
 *           xRot, yRot, zRot (degrees) and the part's offset from its socket (model pixels)
 *   then for each of right arm, left arm, right leg, left leg:
 *           the elbow/knee: hinge rotation and hinge turn in degrees (see BentLimbs)
 * </pre>
 */
public final class MobClips {
	private MobClips() {}

	public static final int BODY_X = 0;
	public static final int BODY_Y = 1;
	public static final int BODY_Z = 2;
	public static final int OFF_X = 3;
	public static final int OFF_Z = 4;
	public static final int AIR = 5;
	public static final int PARTS = 6;
	public static final int PART_SIZE = 6;
	public static final int PART_COUNT = 6;
	public static final int BENDS = PARTS + PART_COUNT * PART_SIZE;
	public static final int BEND_SIZE = 2;
	public static final int LIMB_COUNT = 4;
	public static final int ROW = BENDS + LIMB_COUNT * BEND_SIZE;

	private static Map<String, float[][]> clips;

	@Nullable
	private static float[][] get(String name) {
		if (clips == null) {
			clips = load();
		}
		return clips.get(name);
	}

	public static boolean has(String name) {
		return get(name) != null;
	}

	/** Index of the last frame (the clip lasts this many ticks); 0 if the clip is missing. */
	public static int length(String name) {
		float[][] c = get(name);
		return c == null ? 0 : c.length - 1;
	}

	private static Map<String, float[][]> load() {
		Map<String, float[][]> out = new HashMap<>();
		try (InputStream in = MobClips.class.getResourceAsStream("/assets/freeflow/mob_animations.json")) {
			if (in == null) {
				FreeflowMod.LOGGER.warn("mob_animations.json not found; enemies keep their vanilla animation");
				return out;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				JsonArray rows = e.getValue().getAsJsonArray();
				float[][] clip = new float[rows.size()][ROW];
				for (int i = 0; i < rows.size(); i++) {
					JsonArray row = rows.get(i).getAsJsonArray();
					if (row.size() != ROW) {
						throw new IllegalStateException("clip " + e.getKey() + " has rows of " + row.size());
					}
					for (int j = 0; j < ROW; j++) {
						clip[i][j] = row.get(j).getAsFloat();
					}
				}
				if (clip.length > 0) {
					out.put(e.getKey(), clip);
				}
			}
		} catch (Exception ex) {
			FreeflowMod.LOGGER.error("Failed to load mob animations", ex);
			out.clear();
		}
		return out;
	}

	/** Pose at fractional tick {@code t}, clamped to the clip. */
	@Nullable
	public static float[] sample(String name, float t) {
		float[][] c = get(name);
		if (c == null) {
			return null;
		}
		float k = Mth.clamp(t, 0, c.length - 1);
		int i = (int) Math.floor(k);
		int j = Math.min(c.length - 1, i + 1);
		return mix(c[i], c[j], k - i, false);
	}

	/** Pose of a looping clip (its rows are one cycle; the row after the last is the first). */
	@Nullable
	public static float[] loop(String name, float t) {
		float[][] c = get(name);
		if (c == null) {
			return null;
		}
		float k = t % c.length;
		if (k < 0) {
			k += c.length;
		}
		int i = (int) Math.floor(k);
		return mix(c[i], c[(i + 1) % c.length], k - i, true);
	}

	@Nullable
	public static float[] last(String name) {
		float[][] c = get(name);
		return c == null ? null : c[c.length - 1].clone();
	}

	@Nullable
	public static float[] first(String name) {
		float[][] c = get(name);
		return c == null ? null : c[0].clone();
	}

	/**
	 * Blend of two poses. Rows of one clip are continuous, so they mix plainly; unrelated poses
	 * ({@code shortest}) turn each angle the short way round.
	 */
	public static float[] mix(float[] a, float[] b, float k, boolean shortest) {
		float[] out = new float[ROW];
		for (int i = 0; i < ROW; i++) {
			boolean angle = i < OFF_X || (i >= PARTS && i < BENDS && (i - PARTS) % PART_SIZE < 3);
			if (angle && shortest) {
				out[i] = a[i] + Mth.wrapDegrees(b[i] - a[i]) * k;
			} else {
				out[i] = Mth.lerp(k, a[i], b[i]);
			}
		}
		return out;
	}
}

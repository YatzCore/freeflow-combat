package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.FreeflowMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motion-capture clips for enemies (assets/freeflow/mob_animations.json, made by
 * tools/mocap.py from the CMU motion capture database). One frame per tick, absolute values:
 * whole-body rotation/offset plus limb angles.
 */
public final class MobClips {
	private MobClips() {}

	public static final String FALL_BACK = "fall_back";
	public static final String GET_UP = "get_up";

	/** One tick of a clip. Angles in degrees, pos in blocks (x right, y up, z back). */
	public record Frame(float[] body, float[] pos, float[] torso, float[] head,
			float[] rightArm, float[] leftArm, float[] rightLeg, float[] leftLeg) {}

	private static Map<String, List<Frame>> clips;

	@Nullable
	public static List<Frame> get(String name) {
		if (clips == null) {
			clips = load();
		}
		return clips.get(name);
	}

	public static int length(String name) {
		List<Frame> c = get(name);
		return c == null ? 0 : c.size();
	}

	private static Map<String, List<Frame>> load() {
		Map<String, List<Frame>> out = new HashMap<>();
		try (InputStream in = MobClips.class.getResourceAsStream("/assets/freeflow/mob_animations.json")) {
			if (in == null) {
				FreeflowMod.LOGGER.warn("mob_animations.json not found; enemies use procedural animation");
				return out;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				List<Frame> frames = new ArrayList<>();
				for (JsonElement f : e.getValue().getAsJsonArray()) {
					JsonObject o = f.getAsJsonObject();
					frames.add(new Frame(vec(o, "body"), vec(o, "pos"), vec(o, "torso"), vec(o, "head"),
							vec(o, "right_arm"), vec(o, "left_arm"), vec(o, "right_leg"), vec(o, "left_leg")));
				}
				out.put(e.getKey(), frames);
			}
		} catch (Exception ex) {
			FreeflowMod.LOGGER.error("Failed to load mob animations", ex);
		}
		return out;
	}

	private static float[] vec(JsonObject o, String key) {
		JsonArray a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	/** Frame at fractional tick {@code t} (clamped), linearly interpolated. */
	@Nullable
	public static Frame sample(String name, float t) {
		List<Frame> c = get(name);
		if (c == null || c.isEmpty()) {
			return null;
		}
		float k = Mth.clamp(t, 0, c.size() - 1);
		int i = (int) Math.floor(k);
		int j = Math.min(c.size() - 1, i + 1);
		float f = k - i;
		Frame a = c.get(i);
		Frame b = c.get(j);
		return new Frame(lerp(a.body, b.body, f), lerp(a.pos, b.pos, f), lerp(a.torso, b.torso, f),
				lerp(a.head, b.head, f), lerp(a.rightArm, b.rightArm, f), lerp(a.leftArm, b.leftArm, f),
				lerp(a.rightLeg, b.rightLeg, f), lerp(a.leftLeg, b.leftLeg, f));
	}

	private static float[] lerp(float[] a, float[] b, float f) {
		return new float[] {Mth.lerp(f, a[0], b[0]), Mth.lerp(f, a[1], b[1]), Mth.lerp(f, a[2], b[2])};
	}

	/** Whole-body transform, same convention as playerAnimator (pivot 0.7 blocks up, scaled to the mob). */
	public static void applyBody(PoseStack pose, Frame f, float heightScale) {
		pose.translate(f.pos[0] * heightScale, f.pos[1] * heightScale, f.pos[2] * heightScale);
		float pivot = 0.7f * heightScale;
		pose.translate(0, pivot, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(f.body[2]));
		pose.mulPose(Axis.YP.rotationDegrees(f.body[1]));
		pose.mulPose(Axis.XP.rotationDegrees(f.body[0]));
		pose.translate(0, -pivot, 0);
	}

	/** Limb angles; {@code w} blends from the current pose (0) to the clip (1). */
	public static void applyLimbs(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
			ModelPart rightLeg, ModelPart leftLeg, Frame f, float w) {
		set(head, f.head, w);
		set(body, f.torso, w);
		set(rightArm, f.rightArm, w);
		set(leftArm, f.leftArm, w);
		set(rightLeg, f.rightLeg, w);
		set(leftLeg, f.leftLeg, w);
	}

	private static void set(ModelPart p, float[] deg, float w) {
		p.xRot = Mth.lerp(w, p.xRot, deg[0] * Mth.DEG_TO_RAD);
		p.yRot = Mth.lerp(w, p.yRot, deg[1] * Mth.DEG_TO_RAD);
		p.zRot = Mth.lerp(w, p.zRot, deg[2] * Mth.DEG_TO_RAD);
	}
}

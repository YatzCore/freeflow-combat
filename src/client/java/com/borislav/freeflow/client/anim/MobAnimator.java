package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.ai.MobReaction;
import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.MobReactionPayload;
import com.borislav.freeflow.network.TelegraphPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Enemy body language, driven by {@link MobReactionPayload}s from the server: wind-ups and
 * swings, head snaps, staggers, being launched, dazed swaying, knock-downs (fall, lie, get up),
 * knock-outs and the paired counter reactions (blocked, kicked away, thrown overhead).
 *
 * Every reaction is a sequence of authored clips ({@link MobClips}). A new reaction always
 * starts from the pose the enemy is in right now (an enemy that is already on the floor is
 * finished on the floor, a dazed enemy that is hit goes back to being dazed), and the body is
 * kept on the floor by {@link GroundFit} whatever the mob's shape.
 *
 * Hooks (LivingEntityRendererMixin): {@link #applyBody} turns the whole body, {@link #applyPose}
 * poses the limbs after vanilla setupAnim, {@link #afterRender} gives the shared model back
 * clean. Humanoid and illager models get full poses; other mobs get the whole-body motion.
 */
public final class MobAnimator {
	private MobAnimator() {}

	private static final String WINDUP = "windup";
	private static final String WINDUP_ARMED = "windup_armed";
	private static final String ATTACK = "attack";
	private static final String ATTACK_ARMED = "attack_armed";
	private static final String HIT_LIGHT_L = "hit_light_l";
	private static final String HIT_LIGHT_R = "hit_light_r";
	private static final String HIT_MEDIUM = "hit_medium";
	private static final String LAUNCHED = "launched";
	private static final String STUNNED = "stunned";
	private static final String FALL_BACK = "fall_back";
	private static final String GET_UP = "get_up";
	private static final String KO_FALL = "ko_fall";
	private static final String KO_GROUND = "ko_ground";
	private static final String COUNTERED_STRIKE = "countered_strike";
	private static final String COUNTERED_KICK = "countered_kick";
	private static final String THROWN = "thrown";
	private static final String BLADE_STRUCK = "blade_struck";
	private static final String HOOKED = "hooked";
	private static final String PULLED = "pulled";
	private static final String CLOTHESLINED = "clotheslined";

	/** Ticks to move from the previous pose into a new reaction. */
	private static final float BLEND_IN = 2f;
	/** Ticks to hand back to the vanilla animation after a reaction ends. */
	private static final float BLEND_OUT = 4f;
	/** Ticks to cross-fade between the clips inside one reaction. */
	private static final float CROSS = 3f;

	private static final class State {
		final MobReaction reaction;
		final long start;
		int duration;
		final int param;
		final int attackerId;
		/** Pose (and its weight) the enemy was in when this reaction began; null = vanilla. */
		@Nullable float[] from;
		float fromWeight;
		/** Already lying on the floor when this reaction began. */
		boolean wasDown;
		/** The swing follows an armed (two-handed) wind-up. */
		boolean armed;
		/** Which way a light hit snaps the head. */
		boolean hitFromRight;
		/** Dazed swaying continues after the opening clip until this many ticks in (0 = no). */
		int dazedUntil;

		State(MobReaction reaction, long start, int duration, int param, int attackerId) {
			this.reaction = reaction;
			this.start = start;
			this.duration = duration;
			this.param = param;
			this.attackerId = attackerId;
		}
	}

	private record Parts(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
			ModelPart rightLeg, ModelPart leftLeg, @Nullable ModelPart hat) {
		ModelPart[] all() {
			return new ModelPart[] {head, body, rightArm, leftArm, rightLeg, leftLeg};
		}

		ModelPart[] limbs() {
			return new ModelPart[] {rightArm, leftArm, rightLeg, leftLeg};
		}
	}

	/** What the renderer should show right now: an absolute pose and how much of it (vs vanilla). */
	private record Shown(float[] pose, float weight) {}

	private static final Map<Integer, State> STATES = new HashMap<>();

	// ------------------------------------------------------------------ state

	public static void onReaction(MobReactionPayload payload) {
		int id = payload.mobId();
		State old = STATES.get(id);
		if (old != null && old.reaction == MobReaction.KNOCKED_OUT) {
			return; // knocked out stays down
		}
		long now = ClientCombo.clientTick;
		State s = new State(payload.reaction(), now, Math.max(1, payload.ticks()), payload.param(), payload.attackerId());

		if (old != null) {
			float age = now - old.start;
			Shown shown = shown(old, age);
			if (shown != null) {
				s.from = shown.pose();
				s.fromWeight = shown.weight();
			}
			s.wasDown = isLying(old, age);
			s.armed = old.reaction == MobReaction.WINDUP && old.param == TelegraphPayload.ARMED;

			// A dazed enemy that takes a hit goes back to being dazed afterwards.
			int dazedLeft = dazedLeft(old, age);
			if (dazedLeft > 0 && (s.reaction == MobReaction.HIT_LIGHT || s.reaction == MobReaction.HIT_MEDIUM)) {
				s.dazedUntil = Math.max(dazedLeft, FreeflowConfig.beatdownStunExtendTicks);
				s.duration = Math.max(s.duration, s.dazedUntil);
			}
		}
		if ((s.reaction == MobReaction.COUNTERED && s.param != MobReaction.COUNTER_THROW)
				|| s.reaction == MobReaction.GADGET_STRUCK || s.reaction == MobReaction.PULLED) {
			s.dazedUntil = s.duration;
		}
		if (s.reaction == MobReaction.HIT_LIGHT) {
			s.hitFromRight = attackerOnRight(id, s.attackerId);
		}
		STATES.put(id, s);
	}

	/** A ground takedown has started on this enemy: it must not get up in the middle of it. */
	public static void holdDown(int mobId, int ticks) {
		State s = STATES.get(mobId);
		if (s != null && isDownReaction(s)) {
			s.duration = Math.max(s.duration, (int) (ClientCombo.clientTick - s.start) + ticks + MobClips.length(GET_UP));
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			STATES.clear();
			return;
		}
		STATES.entrySet().removeIf(e -> {
			State s = e.getValue();
			if (mc.level.getEntity(e.getKey()) == null) {
				return true;
			}
			if (s.reaction == MobReaction.KNOCKED_OUT) {
				return false; // until the entity is removed
			}
			return ClientCombo.clientTick - s.start > s.duration + BLEND_OUT + 1;
		});
	}

	public static void reset() {
		STATES.clear();
	}

	/** On the ground right now (knocked down or thrown, by anyone): a ground takedown can finish it. */
	public static boolean isDown(int mobId) {
		State s = STATES.get(mobId);
		return s != null && isDownReaction(s) && ClientCombo.clientTick - s.start < s.duration;
	}

	@Nullable
	private static State state(LivingEntity entity) {
		return entity instanceof Mob ? STATES.get(entity.getId()) : null;
	}

	private static boolean isDownReaction(State s) {
		return s.reaction == MobReaction.KNOCKED_DOWN || s.reaction == MobReaction.CLOTHESLINED
				|| (s.reaction == MobReaction.COUNTERED && s.param == MobReaction.COUNTER_THROW);
	}

	/** The clip that puts the enemy on the floor in a "down" reaction. */
	private static String fallClip(State s) {
		return switch (s.reaction) {
			case COUNTERED -> THROWN;
			case CLOTHESLINED -> CLOTHESLINED;
			default -> FALL_BACK;
		};
	}

	/** True while the reaction has the enemy flat on the floor. */
	private static boolean isLying(State s, float t) {
		if (s.reaction == MobReaction.KNOCKED_OUT) {
			return t >= MobClips.length(s.wasDown ? KO_GROUND : KO_FALL) - 4;
		}
		if (!isDownReaction(s)) {
			return false;
		}
		int fall = s.wasDown ? 0 : MobClips.length(fallClip(s));
		return t >= fall - 3 && t <= s.duration - MobClips.length(GET_UP) + 4;
	}

	/** Ticks of dazed swaying this reaction still has ahead of it. */
	private static int dazedLeft(State s, float t) {
		if (s.reaction == MobReaction.STUNNED) {
			return Math.round(s.duration - t);
		}
		return s.dazedUntil > 0 ? Math.round(s.dazedUntil - t) : 0;
	}

	private static boolean attackerOnRight(int mobId, int attackerId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || attackerId < 0) {
			return (mobId & 1) == 0;
		}
		Entity mob = mc.level.getEntity(mobId);
		Entity attacker = mc.level.getEntity(attackerId);
		if (!(mob instanceof LivingEntity living) || attacker == null) {
			return (mobId & 1) == 0;
		}
		// Hits come from in front, so alternate with the striking hand: use the attacker's own
		// angle to break the tie when it stands off to one side.
		float toAttacker = (float) Math.toDegrees(Math.atan2(-(attacker.getX() - mob.getX()), attacker.getZ() - mob.getZ()));
		float off = Mth.wrapDegrees(toAttacker - living.yBodyRot);
		if (Math.abs(off) > 25f) {
			return off > 0;
		}
		return ((ClientCombo.clientTick / 3) & 1) == 0;
	}

	// ------------------------------------------------------------------ timelines

	/** The reaction's own pose at {@code t} ticks in (before blending in or out); null = none. */
	@Nullable
	private static float[] timeline(State s, float t) {
		return switch (s.reaction) {
			case WINDUP -> switch (s.param) {
				case TelegraphPayload.RANGED -> null; // the bow draw is vanilla's
				case TelegraphPayload.ARMED -> MobClips.sample(WINDUP_ARMED, t);
				default -> MobClips.sample(WINDUP, t);
			};
			case ATTACK -> MobClips.sample(s.armed ? ATTACK_ARMED : ATTACK, t);
			case HIT_LIGHT -> thenDazed(s, s.hitFromRight ? HIT_LIGHT_R : HIT_LIGHT_L, t);
			case HIT_MEDIUM -> thenDazed(s, HIT_MEDIUM, t);
			case LAUNCHED -> MobClips.sample(LAUNCHED, t);
			case STUNNED -> MobClips.loop(STUNNED, t);
			case KNOCKED_DOWN -> down(s, FALL_BACK, t);
			case KNOCKED_OUT -> MobClips.sample(s.wasDown ? KO_GROUND : KO_FALL, t);
			case GADGET_STRUCK -> thenDazed(s, BLADE_STRUCK, t);
			case HOOKED -> MobClips.sample(HOOKED, t);
			case PULLED -> thenDazed(s, PULLED, t);
			case CLOTHESLINED -> down(s, CLOTHESLINED, t);
			case COUNTERED -> switch (s.param) {
				case MobReaction.COUNTER_THROW -> down(s, THROWN, t);
				case MobReaction.COUNTER_KICK -> thenDazed(s, COUNTERED_KICK, t);
				default -> thenDazed(s, COUNTERED_STRIKE, t);
			};
		};
	}

	/** An opening clip, then (if the enemy is dazed) the dazed sway. */
	@Nullable
	private static float[] thenDazed(State s, String clip, float t) {
		int len = MobClips.length(clip);
		if (t <= len || s.dazedUntil <= len) {
			return MobClips.sample(clip, t);
		}
		float[] end = MobClips.last(clip);
		float[] sway = MobClips.loop(STUNNED, t - len);
		if (end == null || sway == null) {
			return end;
		}
		return MobClips.mix(end, sway, smooth((t - len) / CROSS), true);
	}

	/** Falls (unless already down), lies still, gets up over the end of the reaction. */
	@Nullable
	private static float[] down(State s, String fallClip, float t) {
		int up = MobClips.length(GET_UP);
		if (up > 0 && t > s.duration - up) {
			return MobClips.sample(GET_UP, t - (s.duration - up));
		}
		if (s.wasDown) {
			return MobClips.first(GET_UP);
		}
		return MobClips.sample(fallClip, t);
	}

	/** Pose and weight to draw at {@code t} ticks into the reaction; null if it has no pose. */
	@Nullable
	private static Shown shown(State s, float t) {
		float[] pose = timeline(s, t);
		if (pose == null) {
			return null;
		}
		float weight = 1f;
		if (t < BLEND_IN) {
			float k = smooth(t / BLEND_IN);
			if (s.from != null) {
				pose = MobClips.mix(s.from, pose, k, true);
				weight = Mth.lerp(k, s.fromWeight, 1f);
			} else {
				weight = k;
			}
		}
		if (s.reaction != MobReaction.KNOCKED_OUT && t > s.duration) {
			weight *= Mth.clamp(1f - (t - s.duration) / BLEND_OUT, 0f, 1f);
		}
		return new Shown(pose, weight);
	}

	@Nullable
	private static Shown shown(LivingEntity entity, float partialTick) {
		State s = state(entity);
		return s == null ? null : shown(s, Math.max(0f, ClientCombo.clientTick + partialTick - s.start));
	}

	// ------------------------------------------------------------------ render hooks

	/** Keeps knocked-out mobs from doing the vanilla red tip-over (they fall our way instead). */
	public static void beforeRender(LivingEntity entity) {
		State s = state(entity);
		if (s != null && s.reaction == MobReaction.KNOCKED_OUT) {
			entity.deathTime = 0;
		}
	}

	/**
	 * Called at the end of setupRotations: the pose stack is at the mob's feet, turned to its
	 * body yaw (x = its right, y = up, z = its back).
	 */
	public static void applyBody(LivingEntity entity, EntityModel<?> model, PoseStack pose, float partialTick) {
		Shown shown = shown(entity, partialTick);
		if (shown == null || shown.weight() <= 0f) {
			return;
		}
		float[] p = shown.pose();
		float w = shown.weight();
		float hip = hipHeight(entity, model);
		float size = hip / 0.75f;
		pose.translate(p[MobClips.OFF_X] * size * w, hip, p[MobClips.OFF_Z] * size * w);
		pose.mulPose(Axis.ZP.rotationDegrees(turn(p[MobClips.BODY_Z], w)));
		pose.mulPose(Axis.YP.rotationDegrees(turn(p[MobClips.BODY_Y], w)));
		pose.mulPose(Axis.XP.rotationDegrees(turn(p[MobClips.BODY_X], w)));
		pose.translate(0f, -hip, 0f);
	}

	/** A whole-body angle at blend weight w (full turns are dropped while blending). */
	private static float turn(float degrees, float w) {
		return w >= 1f ? degrees : Mth.wrapDegrees(degrees) * w;
	}

	/** Called right after the model's setupAnim: poses the limbs and puts the body on the floor. */
	public static void applyPose(LivingEntity entity, EntityModel<?> model, PoseStack pose, float partialTick) {
		Shown shown = shown(entity, partialTick);
		if (shown == null || shown.weight() <= 0f) {
			return;
		}
		float[] p = shown.pose();
		float w = shown.weight();
		Parts parts = parts(model);
		if (parts != null) {
			ModelPart[] all = parts.all();
			for (int i = 0; i < all.length; i++) {
				set(all[i], p, MobClips.PARTS + i * MobClips.PART_SIZE, w);
			}
			if (parts.hat() != null) {
				parts.hat().copyFrom(parts.head());
			}
			ModelPart[] limbs = parts.limbs();
			for (int i = 0; i < limbs.length; i++) {
				int at = MobClips.BENDS + i * MobClips.BEND_SIZE;
				BentLimbs.set(limbs[i], p[at] * w, p[at + 1]);
			}
		}

		float low = GroundFit.lowest(pose, model);
		if (Float.isNaN(low)) {
			return;
		}
		if (parts == null) {
			// Other body shapes (spiders, creepers, things that hover): only keep them out of the floor.
			GroundFit.lift(pose, Math.max(0f, -low));
			return;
		}
		float air = p[MobClips.AIR] * hipHeight(entity, model) / 0.75f;
		float contact = (air - low) * w;          // lowest point exactly `air` above the floor
		GroundFit.lift(pose, Math.max(contact, -low)); // and never below it while blending
	}

	/** The model is shared by every mob of its type: undo what the pose changed. */
	public static void afterRender(LivingEntity entity, EntityModel<?> model) {
		if (state(entity) == null) {
			return;
		}
		Parts parts = parts(model);
		if (parts != null) {
			for (ModelPart part : parts.all()) {
				part.resetPose();
			}
			if (parts.hat() != null) {
				parts.hat().resetPose();
			}
		}
	}

	private static void set(ModelPart part, float[] p, int at, float w) {
		part.xRot = turnTo(part.xRot, p[at], w);
		part.yRot = turnTo(part.yRot, p[at + 1], w);
		part.zRot = turnTo(part.zRot, p[at + 2], w);
		// Offsets are from the part's own socket, so they suit any humanoid's proportions.
		PartPose socket = part.getInitialPose();
		part.x = Mth.lerp(w, part.x, socket.x + p[at + 3]);
		part.y = Mth.lerp(w, part.y, socket.y + p[at + 4]);
		part.z = Mth.lerp(w, part.z, socket.z + p[at + 5]);
	}

	private static float turnTo(float currentRad, float targetDeg, float w) {
		float target = targetDeg * Mth.DEG_TO_RAD;
		if (w >= 1f) {
			return target;
		}
		return currentRad + Mth.wrapDegrees((target - currentRad) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * w;
	}

	/** Height of the hip joints above the feet in the entity's own (unscaled) space. */
	private static float hipHeight(LivingEntity entity, EntityModel<?> model) {
		Parts parts = parts(model);
		if (parts != null) {
			float hip = (24f - parts.rightLeg().getInitialPose().y) / 16f;
			if (model.young) {
				hip *= 0.5f;
			}
			if (hip > 0.1f) {
				return hip;
			}
		}
		return entity.getBbHeight() / Math.max(0.01f, entity.getScale()) * 0.45f;
	}

	@Nullable
	private static Parts parts(EntityModel<?> model) {
		if (model instanceof HumanoidModel<?> h) {
			return new Parts(h.head, h.body, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg, h.hat);
		}
		if (model instanceof HierarchicalModel<?> m) {
			ModelPart root = m.root();
			ModelPart head = child(root, "head");
			ModelPart body = child(root, "body");
			ModelPart rightArm = child(root, "right_arm");
			ModelPart leftArm = child(root, "left_arm");
			ModelPart rightLeg = child(root, "right_leg");
			ModelPart leftLeg = child(root, "left_leg");
			if (head != null && body != null && rightArm != null && leftArm != null && rightLeg != null && leftLeg != null) {
				return new Parts(head, body, rightArm, leftArm, rightLeg, leftLeg, null);
			}
		}
		return null;
	}

	/** The model part of an arm, for what it holds (null if this model has no such part). */
	@Nullable
	public static ModelPart armPart(EntityModel<?> model, net.minecraft.world.entity.HumanoidArm arm) {
		Parts parts = parts(model);
		if (parts == null) {
			return null;
		}
		return arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? parts.rightArm() : parts.leftArm();
	}

	@Nullable
	private static ModelPart child(ModelPart root, String name) {
		return root.hasChild(name) ? root.getChild(name) : null;
	}

	private static float smooth(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return k * k * (3f - 2f * k);
	}
}

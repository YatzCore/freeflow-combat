package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.ai.MobReaction;
import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.network.MobReactionPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Arkham-style enemy animation, driven by {@link MobReactionPayload}s from the server:
 * attack wind-ups and swings, head snaps, stagger-backs, backward rolls, stunned sway,
 * knock-downs (fall, lie, get up), knock-outs and paired counter reactions (incl. being
 * thrown over the player's shoulder).
 *
 * Two hooks (LivingEntityRendererMixin): {@link #applyBody} moves/rotates the whole body
 * (rolls, falls, throws) and {@link #applyPose} poses the limbs after vanilla setupAnim.
 * Humanoid and illager models get full poses; other mobs get whole-body motion only.
 */
public final class MobAnimator {
	private MobAnimator() {}

	private record State(MobReaction reaction, long start, int duration, int param, int attackerId) {}

	private record Parts(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
			ModelPart rightLeg, ModelPart leftLeg, @Nullable ModelPart hat) {}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private static final int FALL_TICKS = 6;
	private static final int GET_UP_TICKS = 10;
	private static final int THROW_TICKS = 12;
	private static final int ROLL_TICKS = 12;

	public static void onReaction(MobReactionPayload payload) {
		State current = STATES.get(payload.mobId());
		if (current != null && current.reaction() == MobReaction.KNOCKED_OUT) {
			return; // knocked out stays down
		}
		STATES.put(payload.mobId(), new State(payload.reaction(), ClientCombo.clientTick,
				Math.max(1, payload.ticks()), payload.param(), payload.attackerId()));
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			STATES.clear();
			return;
		}
		STATES.entrySet().removeIf(e -> {
			State s = e.getValue();
			Entity entity = mc.level.getEntity(e.getKey());
			if (entity == null) {
				return true;
			}
			if (s.reaction() == MobReaction.KNOCKED_OUT) {
				return false; // until the entity is removed
			}
			long age = ClientCombo.clientTick - s.start();
			return age > s.duration() + 4;
		});
	}

	public static void reset() {
		STATES.clear();
	}

	@Nullable
	private static State state(LivingEntity entity) {
		return entity instanceof Mob ? STATES.get(entity.getId()) : null;
	}

	/** Keeps knocked-out mobs from doing the vanilla red tip-over (they fall our way instead). */
	public static void beforeRender(LivingEntity entity) {
		State s = state(entity);
		if (s != null && s.reaction() == MobReaction.KNOCKED_OUT) {
			entity.deathTime = 0;
		}
	}

	// ------------------------------------------------------------------ whole body

	/**
	 * Called at the end of setupRotations: the pose stack is at the mob's feet, rotated to its
	 * body yaw. Local -Z is the mob's front, so +X rotation tips it backward.
	 */
	public static void applyBody(LivingEntity entity, PoseStack pose, float partialTick) {
		State s = state(entity);
		if (s == null) {
			return;
		}
		float t = ClientCombo.clientTick + partialTick - s.start();
		float d = s.duration();
		float h = entity.getBbHeight() / Math.max(0.01f, entity.getScale());

		switch (s.reaction()) {
			case HIT_MEDIUM -> tilt(pose, 22f * bell(t / d), 0);
			case ATTACK -> tilt(pose, -14f * bell(t / d), 0); // lunges into the swing
			case WINDUP -> {
				if (s.param() != 2) {
					tilt(pose, 10f * easeOut(clamp01(t / d)), 0); // leans back to load the hit
				}
			}
			case LAUNCHED -> roll(pose, t, h);
			case STUNNED -> sway(pose, t, fadeInOut(t, d, 4));
			case KNOCKED_DOWN -> {
				MobClips.Frame f = downFrame(t, d, true);
				if (f != null) {
					MobClips.applyBody(pose, f, h / 1.8f);
				} else {
					lie(pose, lyingAngle(t, d, true));
				}
			}
			case KNOCKED_OUT -> {
				MobClips.Frame f = downFrame(t, Float.MAX_VALUE, false);
				if (f != null) {
					MobClips.applyBody(pose, f, h / 1.8f);
				} else {
					lie(pose, lyingAngle(t, Float.MAX_VALUE, false));
				}
			}
			case COUNTERED -> {
				switch (s.param()) {
					case MobReaction.COUNTER_THROW -> {
					MobClips.Frame f = t >= THROW_TICKS ? lyingFrame(t, d) : null;
					if (f != null) {
						MobClips.applyBody(pose, f, h / 1.8f);
					} else {
						thrown(pose, t, d, h);
					}
				}
					case MobReaction.COUNTER_KICK -> {
						if (t < ROLL_TICKS + 2) {
							roll(pose, t, h);
						} else {
							sway(pose, t, fadeInOut(t - ROLL_TICKS - 2, d - ROLL_TICKS - 2, 4));
						}
					}
					default -> {
						if (t < 10) {
							tilt(pose, 26f * bell(t / 10f), 0);
						} else {
							sway(pose, t, fadeInOut(t - 10, d - 10, 4));
						}
					}
				}
			}
			default -> {}
		}
	}

	private static void tilt(PoseStack pose, float backDegrees, float sideDegrees) {
		pose.mulPose(Axis.XP.rotationDegrees(backDegrees));
		if (sideDegrees != 0) {
			pose.mulPose(Axis.ZP.rotationDegrees(sideDegrees));
		}
	}

	/** Knocked off its feet: a backward roll around the waist with a little air. */
	private static void roll(PoseStack pose, float t, float h) {
		float k = clamp01(t / ROLL_TICKS);
		if (k >= 1f) {
			tilt(pose, 12f * bell(clamp01((t - ROLL_TICKS) / 6f)), 0);
			return;
		}
		pose.translate(0, Math.sin(Math.PI * k) * 0.5 + h * 0.5, 0);
		pose.mulPose(Axis.XP.rotationDegrees(360f * easeInOut(k)));
		pose.translate(0, -h * 0.5, 0);
	}

	private static void sway(PoseStack pose, float t, float weight) {
		if (weight <= 0) {
			return;
		}
		pose.mulPose(Axis.ZP.rotationDegrees(7f * Mth.sin(t * 0.3f) * weight));
		pose.mulPose(Axis.XP.rotationDegrees(5f * weight));
	}

	/** Lying on the back: angle 0 (standing) to 90 (flat), lifted so it doesn't sink into the floor. */
	private static void lie(PoseStack pose, float angle) {
		pose.translate(0, 0.15 * (angle / 90f), 0);
		pose.mulPose(Axis.XP.rotationDegrees(angle));
	}

	/** Fall (with a bounce), lie, and (optionally) get up at the end of {@code d}. */
	private static float lyingAngle(float t, float d, boolean getsUp) {
		if (t < FALL_TICKS) {
			return 90f * easeIn(t / FALL_TICKS);
		}
		if (t < FALL_TICKS + 4) {
			return 90f - 9f * Mth.sin((float) Math.PI * (t - FALL_TICKS) / 4f);
		}
		if (getsUp && t > d - GET_UP_TICKS) {
			return 90f * (1f - easeOut(clamp01((t - (d - GET_UP_TICKS)) / GET_UP_TICKS)));
		}
		return 90f;
	}

	/** Counter throw: flipped forward over the player, landing on its back, then getting up. */
	private static void thrown(PoseStack pose, float t, float d, float h) {
		if (t < THROW_TICKS) {
			float k = clamp01(t / THROW_TICKS);
			pose.translate(0, Math.sin(Math.PI * k) * 1.2 + h * 0.5 * (1 - k), 0);
			pose.mulPose(Axis.XP.rotationDegrees(-270f * easeInOut(k)));
			pose.translate(0, -h * 0.5 * (1 - k), 0);
			return;
		}
		// -270 == +90: now lying on its back.
		float lt = t - THROW_TICKS;
		float angle = 90f;
		if (lt < 3) {
			angle = 90f - 10f * Mth.sin((float) Math.PI * lt / 3f);
		} else if (t > d - GET_UP_TICKS) {
			angle = 90f * (1f - easeOut(clamp01((t - (d - GET_UP_TICKS)) / GET_UP_TICKS)));
		}
		lie(pose, angle);
	}

	// ------------------------------------------------------------------ mocap (fall / lie / get up)

	/** The motion-captured fall plays this much faster than recorded (snappier knockdowns). */
	private static final float FALL_SPEED = 1.5f;

	/** Knocked down: mocap fall onto the back, lie still, mocap get-up over the last part of {@code d}. */
	@Nullable
	private static MobClips.Frame downFrame(float t, float d, boolean getsUp) {
		if (MobClips.length(MobClips.FALL_BACK) == 0) {
			return null;
		}
		int up = MobClips.length(MobClips.GET_UP);
		if (getsUp && up > 0 && t > d - up) {
			return MobClips.sample(MobClips.GET_UP, t - (d - up));
		}
		return MobClips.sample(MobClips.FALL_BACK, t * FALL_SPEED);
	}

	/** Already on the ground (after a throw): hold the lying pose, then get up at the end. */
	@Nullable
	private static MobClips.Frame lyingFrame(float t, float d) {
		if (MobClips.length(MobClips.FALL_BACK) == 0) {
			return null;
		}
		int up = MobClips.length(MobClips.GET_UP);
		if (up > 0 && t > d - up) {
			return MobClips.sample(MobClips.GET_UP, t - (d - up));
		}
		return MobClips.sample(MobClips.FALL_BACK, Float.MAX_VALUE);
	}

	private static void clipLimbs(Parts p, MobClips.Frame f) {
		MobClips.applyLimbs(p.head(), p.body(), p.rightArm(), p.leftArm(), p.rightLeg(), p.leftLeg(), f, 1f);
	}

	// ------------------------------------------------------------------ limbs

	/** Called right after the model's setupAnim: overrides/adds to the limb angles. */
	public static void applyPose(LivingEntity entity, EntityModel<?> model, float partialTick) {
		State s = state(entity);
		if (s == null) {
			return;
		}
		Parts p = parts(model);
		if (p == null) {
			return;
		}
		float t = ClientCombo.clientTick + partialTick - s.start();
		float d = s.duration();

		switch (s.reaction()) {
			case WINDUP -> windup(p, s.param(), easeOut(clamp01(t / d)));
			case ATTACK -> attack(p, s.param(), clamp01(t / d));
			case HIT_LIGHT -> headSnap(p, side(entity, s), bell(t / d));
			case HIT_MEDIUM -> stagger(p, bell(t / d));
			case LAUNCHED -> tuck(p, t < ROLL_TICKS ? bell(t / ROLL_TICKS) : 0);
			case STUNNED -> dazed(p, t, fadeInOut(t, d, 4));
			case KNOCKED_DOWN -> {
				MobClips.Frame f = downFrame(t, d, true);
				if (f != null) {
					clipLimbs(p, f);
				} else {
					sprawl(p, lyingAngle(t, d, true) / 90f);
				}
			}
			case KNOCKED_OUT -> {
				MobClips.Frame f = downFrame(t, Float.MAX_VALUE, false);
				if (f != null) {
					clipLimbs(p, f);
				} else {
					sprawl(p, lyingAngle(t, Float.MAX_VALUE, false) / 90f);
				}
			}
			case COUNTERED -> {
				switch (s.param()) {
					case MobReaction.COUNTER_THROW -> {
						MobClips.Frame f = t >= THROW_TICKS ? lyingFrame(t, d) : null;
						if (t < THROW_TICKS) {
							flail(p, bell(t / THROW_TICKS));
						} else if (f != null) {
							clipLimbs(p, f);
						} else {
							float lt = t - THROW_TICKS;
							float up = t > d - GET_UP_TICKS ? clamp01((t - (d - GET_UP_TICKS)) / GET_UP_TICKS) : 0;
							sprawl(p, lt < 1 ? lt : 1f - up);
						}
					}
					case MobReaction.COUNTER_KICK -> {
						if (t < ROLL_TICKS + 2) {
							tuck(p, bell(t / ROLL_TICKS));
						} else {
							dazed(p, t, fadeInOut(t - ROLL_TICKS - 2, d - ROLL_TICKS - 2, 4));
						}
					}
					default -> {
						if (t < 10) {
							headSnap(p, side(entity, s), bell(t / 10f));
							stagger(p, bell(t / 10f));
						} else {
							dazed(p, t, fadeInOut(t - 10, d - 10, 4));
						}
					}
				}
			}
		}
		if (p.hat() != null) {
			p.hat().copyFrom(p.head());
		}
	}

	/** Wind-up: attacking arm cocked back overhead (armed: weapon raised with both hands). */
	private static void windup(Parts p, int kind, float k) {
		if (kind == 2) {
			return; // ranged: the bow draw is already animated by vanilla
		}
		if (kind == 1) {
			p.rightArm().xRot = Mth.lerp(k, p.rightArm().xRot, -3.0f);
			p.leftArm().xRot = Mth.lerp(k, p.leftArm().xRot, -3.0f);
			p.rightArm().zRot = Mth.lerp(k, p.rightArm().zRot, 0.25f);
			p.leftArm().zRot = Mth.lerp(k, p.leftArm().zRot, -0.25f);
		} else {
			p.rightArm().xRot = Mth.lerp(k, p.rightArm().xRot, -2.9f);
			p.rightArm().zRot = Mth.lerp(k, p.rightArm().zRot, -0.45f);
			p.leftArm().xRot = Mth.lerp(k, p.leftArm().xRot, -0.9f);
		}
		p.head().xRot -= 0.15f * k;
	}

	/** The swing: fast chop from overhead to low, then settle back to the vanilla pose. */
	private static void attack(Parts p, int kind, float k) {
		float swing = k < 0.45f ? easeIn(k / 0.45f) : 1f;
		float settle = k < 0.45f ? 0f : (k - 0.45f) / 0.55f;
		float from = -2.9f;
		float to = -0.2f;
		float angle = Mth.lerp(swing, from, to);
		p.rightArm().xRot = Mth.lerp(settle, angle, p.rightArm().xRot);
		if (kind == 1) {
			p.leftArm().xRot = Mth.lerp(settle, angle, p.leftArm().xRot);
		}
		p.body().xRot += 0.3f * bell(k);
	}

	private static void headSnap(Parts p, float side, float s) {
		p.head().yRot += side * 0.75f * s;
		p.head().xRot -= 0.45f * s;
		p.body().yRot += side * 0.3f * s;
		p.rightArm().zRot += 0.5f * s;
		p.leftArm().zRot -= 0.5f * s;
	}

	private static void stagger(Parts p, float s) {
		p.head().xRot -= 0.6f * s;
		p.rightArm().zRot += 0.95f * s;
		p.leftArm().zRot -= 0.95f * s;
		p.rightArm().xRot = Mth.lerp(s, p.rightArm().xRot, -0.4f);
		p.leftArm().xRot = Mth.lerp(s, p.leftArm().xRot, -0.4f);
		p.rightLeg().xRot += 0.5f * s;
		p.leftLeg().xRot -= 0.25f * s;
	}

	/** Curled up during a roll. */
	private static void tuck(Parts p, float w) {
		p.rightArm().xRot = Mth.lerp(w, p.rightArm().xRot, -2.2f);
		p.leftArm().xRot = Mth.lerp(w, p.leftArm().xRot, -2.2f);
		p.rightLeg().xRot = Mth.lerp(w, p.rightLeg().xRot, -1.4f);
		p.leftLeg().xRot = Mth.lerp(w, p.leftLeg().xRot, -1.4f);
		p.head().xRot = Mth.lerp(w, p.head().xRot, 0.6f);
	}

	/** Arms and legs thrown out while flying through the air. */
	private static void flail(Parts p, float w) {
		p.rightArm().xRot = Mth.lerp(w, p.rightArm().xRot, -2.8f);
		p.leftArm().xRot = Mth.lerp(w, p.leftArm().xRot, -2.4f);
		p.rightArm().zRot = Mth.lerp(w, p.rightArm().zRot, 0.6f);
		p.leftArm().zRot = Mth.lerp(w, p.leftArm().zRot, -0.6f);
		p.rightLeg().zRot = Mth.lerp(w, p.rightLeg().zRot, 0.4f);
		p.leftLeg().zRot = Mth.lerp(w, p.leftLeg().zRot, -0.4f);
	}

	/** Dazed: arms hang limp and the head lolls. */
	private static void dazed(Parts p, float t, float w) {
		if (w <= 0) {
			return;
		}
		p.rightArm().xRot = Mth.lerp(w, p.rightArm().xRot, 0.1f + 0.15f * Mth.sin(t * 0.3f));
		p.leftArm().xRot = Mth.lerp(w, p.leftArm().xRot, 0.1f + 0.15f * Mth.sin(t * 0.3f + 1.0f));
		p.rightArm().zRot = Mth.lerp(w, p.rightArm().zRot, 0.12f);
		p.leftArm().zRot = Mth.lerp(w, p.leftArm().zRot, -0.12f);
		p.head().xRot = Mth.lerp(w, p.head().xRot, 0.45f + 0.1f * Mth.sin(t * 0.25f));
		p.head().yRot = Mth.lerp(w, p.head().yRot, 0.35f * Mth.sin(t * 0.18f));
	}

	/** Spread-eagle on the ground (w 0..1 blends from the normal pose). */
	private static void sprawl(Parts p, float w) {
		w = clamp01(w);
		if (w <= 0) {
			return;
		}
		p.rightArm().xRot = Mth.lerp(w, p.rightArm().xRot, 0f);
		p.leftArm().xRot = Mth.lerp(w, p.leftArm().xRot, 0f);
		p.rightArm().zRot = Mth.lerp(w, p.rightArm().zRot, 1.2f);
		p.leftArm().zRot = Mth.lerp(w, p.leftArm().zRot, -1.2f);
		p.rightLeg().xRot = Mth.lerp(w, p.rightLeg().xRot, 0f);
		p.leftLeg().xRot = Mth.lerp(w, p.leftLeg().xRot, 0f);
		p.rightLeg().zRot = Mth.lerp(w, p.rightLeg().zRot, 0.18f);
		p.leftLeg().zRot = Mth.lerp(w, p.leftLeg().zRot, -0.18f);
		p.head().xRot = Mth.lerp(w, p.head().xRot, 0f);
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

	@Nullable
	private static ModelPart child(ModelPart root, String name) {
		return root.hasChild(name) ? root.getChild(name) : null;
	}

	/** +1 / -1: which way to twist away from the attacker. */
	private static float side(LivingEntity entity, State s) {
		Minecraft mc = Minecraft.getInstance();
		Entity attacker = mc.level != null && s.attackerId() >= 0 ? mc.level.getEntity(s.attackerId()) : null;
		if (attacker == null) {
			return 1f;
		}
		float toAttacker = (float) Math.toDegrees(Math.atan2(-(attacker.getX() - entity.getX()), attacker.getZ() - entity.getZ()));
		return Mth.wrapDegrees(toAttacker - entity.yBodyRot) > 0 ? -1f : 1f;
	}

	// ------------------------------------------------------------------ easing

	private static float clamp01(float v) {
		return Mth.clamp(v, 0f, 1f);
	}

	/** 0 -> 1 -> 0 over k in [0,1]. */
	private static float bell(float k) {
		return Mth.sin((float) Math.PI * clamp01(k));
	}

	private static float easeIn(float k) {
		k = clamp01(k);
		return k * k;
	}

	private static float easeOut(float k) {
		k = clamp01(k);
		return 1f - (1f - k) * (1f - k);
	}

	private static float easeInOut(float k) {
		k = clamp01(k);
		return k < 0.5f ? 2f * k * k : 1f - (float) Math.pow(-2f * k + 2f, 2) / 2f;
	}

	/** Blend weight that fades in over {@code fade} ticks and out over the last {@code fade}. */
	private static float fadeInOut(float t, float d, float fade) {
		return clamp01(Math.min(t / fade, (d - t) / fade));
	}
}

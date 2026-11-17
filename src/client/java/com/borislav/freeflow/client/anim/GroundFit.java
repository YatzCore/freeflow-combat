package com.borislav.freeflow.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Keeps animated bodies out of the floor. After a model has been posed, this measures where
 * its lowest box corner actually ends up relative to the entity's feet (whatever the mob's
 * proportions, scale or age, bent limbs included) and lifts the whole render so that point sits
 * on the floor, or at a chosen height above it. Rolling, lying, kneeling and crouching poses can
 * therefore never sink into the ground, and never hover over it either.
 */
public final class GroundFit {
	private GroundFit() {}

	private static final String[] LIMBS = {"right_arm", "left_arm", "right_leg", "left_leg"};

	/** Pose-stack matrix at the entity's feet, captured when its render starts. */
	private static final Matrix4f FEET = new Matrix4f();
	private static final Matrix4f FEET_INVERSE = new Matrix4f();
	private static boolean captured = false;

	public static void begin(PoseStack pose) {
		FEET.set(pose.last().pose());
		FEET.invert(FEET_INVERSE);
		captured = true;
	}

	public static void end() {
		captured = false;
	}

	/** Lowest corner so far, and the limb whose own boxes are being measured (its bend applies). */
	private static final class Measure implements ModelPart.Visitor {
		float low = Float.POSITIVE_INFINITY;
		ModelPart limb;
		ModelPart root;
		private final Vector3f corner = new Vector3f();

		@Override
		public void visit(PoseStack.Pose at, String path, int index, ModelPart.Cube cube) {
			ModelPart bent = null;
			if (limb != null && path.isEmpty()) {
				bent = limb;
			} else if (root != null && path.length() > 1 && path.indexOf('/', 1) < 0) {
				String name = path.substring(1);
				for (String candidate : LIMBS) {
					if (candidate.equals(name) && root.hasChild(name)) {
						bent = root.getChild(name);
					}
				}
			}
			float bend = bent == null ? 0f : BentLimbs.bend(bent);
			float turn = bent == null ? 0f : BentLimbs.turn(bent);
			float mid = (cube.minY + cube.maxY) * 0.5f;
			for (int i = 0; i < 4; i++) {
				float x = (i & 1) == 0 ? cube.minX : cube.maxX;
				float z = (i & 2) == 0 ? cube.minZ : cube.maxZ;
				point(at, cube, x, cube.minY, z, bend, turn, -1);
				point(at, cube, x, cube.maxY, z, bend, turn, 1);
				if (bend != 0f) {
					point(at, cube, x, mid, z, bend, turn, 0);
				}
			}
		}

		private void point(PoseStack.Pose at, ModelPart.Cube cube, float x, float y, float z, float bend, float turn, int side) {
			corner.set(x, y, z);
			BentLimbs.bendPoint(corner, cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ, bend, turn, side);
			at.pose().transformPosition(corner.x / 16f, corner.y / 16f, corner.z / 16f, corner);
			low = Math.min(low, corner.y);
		}

		void limb(ModelPart part, PoseStack stack) {
			limb = part;
			part.visit(stack, this);
			limb = null;
		}
	}

	/**
	 * Height (blocks, + = above the feet) of the lowest corner of the posed model, or NaN if it
	 * can't be measured. Call after setupAnim, with the pose stack as it will be used to render.
	 */
	public static float lowest(PoseStack pose, EntityModel<?> model) {
		if (!captured) {
			return Float.NaN;
		}
		Matrix4f toFeet = new Matrix4f(FEET_INVERSE).mul(pose.last().pose());
		Measure measure = new Measure();
		PoseStack stack = new PoseStack();
		stack.last().pose().set(toFeet);
		if (model instanceof HumanoidModel<?> h) {
			if (h.young) {
				// AgeableListModel shrinks babies while drawing: head 0.75x, the rest 0.5x.
				stack.pushPose();
				stack.scale(0.75f, 0.75f, 0.75f);
				stack.translate(0f, 1f, 0f);
				h.head.visit(stack, measure);
				stack.popPose();
				stack.scale(0.5f, 0.5f, 0.5f);
				stack.translate(0f, 1.5f, 0f);
			} else {
				h.head.visit(stack, measure);
			}
			h.body.visit(stack, measure);
			measure.limb(h.rightArm, stack);
			measure.limb(h.leftArm, stack);
			measure.limb(h.rightLeg, stack);
			measure.limb(h.leftLeg, stack);
		} else if (model instanceof HierarchicalModel<?> m) {
			measure.root = m.root();
			m.root().visit(stack, measure);
		} else {
			return Float.NaN;
		}
		return Float.isInfinite(measure.low) ? Float.NaN : measure.low;
	}

	/** Moves the render straight up (in the entity's own space) by {@code blocks}. */
	public static void lift(PoseStack pose, float blocks) {
		if (!captured || blocks == 0f || Float.isNaN(blocks)) {
			return;
		}
		Matrix4f current = pose.last().pose();
		Matrix4f moved = new Matrix4f(FEET).translate(0f, blocks, 0f).mul(FEET_INVERSE).mul(current);
		current.set(moved);
	}

	/**
	 * Lifts the render just enough that nothing is below the floor (never lowers it). Returns
	 * the height of the lowest point before the lift, blocks (NaN if it can't be measured).
	 */
	public static float keepAbove(PoseStack pose, EntityModel<?> model) {
		float low = lowest(pose, model);
		if (!Float.isNaN(low) && low < 0f) {
			lift(pose, -low);
		}
		return low;
	}
}

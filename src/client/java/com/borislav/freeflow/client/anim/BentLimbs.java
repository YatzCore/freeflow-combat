package com.borislav.freeflow.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Elbows and knees. Minecraft's limbs are single boxes; this draws a limb bent in the middle
 * instead, for players and mobs alike, with no extra library.
 *
 * A model part carries a bend ({@link Part}, added to every ModelPart by ModelPartMixin). When it
 * is not zero the part's boxes are drawn here: each box is cut across its middle, the lower half
 * is turned about a hinge through the centre of the cut, and the ring of corners on the cut is
 * laid on the plane halfway between the two halves so the joint stays closed. The skin's own
 * texture coordinates are kept, so any skin, armour or mob texture bends with the limb.
 *
 * tools/rig.py ({@code bend_point}) is the same maths: the animations are posed and put on the
 * floor with it, and {@link GroundFit} measures the bent shape with {@link #bendPoint} too.
 */
public final class BentLimbs {
	private BentLimbs() {}

	/** Bend state of a model part (implemented on ModelPart by ModelPartMixin). */
	public interface Part {
		/** Rotation of the lower half about the hinge, radians (0 = straight). */
		float freeflow$bend();

		/** The hinge is the part's x axis turned this far about the limb's length, radians. */
		float freeflow$bendTurn();

		void freeflow$setBend(float bend, float turn);
	}

	/** How far the outer corner of a joint may be pushed out to keep it closed (as in rig.py). */
	private static final float MAX_JOINT_STRETCH = 1.6f;

	/** Parts bent during the entity being drawn: models are shared, so they are straightened after. */
	private static final List<ModelPart> BENT = new ArrayList<>();

	public static float bend(ModelPart part) {
		return ((Part) (Object) part).freeflow$bend();
	}

	public static float turn(ModelPart part) {
		return ((Part) (Object) part).freeflow$bendTurn();
	}

	/** Bends a part (degrees) for the entity being drawn. */
	public static void set(ModelPart part, float bendDegrees, float turnDegrees) {
		setRadians(part, bendDegrees * Mth.DEG_TO_RAD, turnDegrees * Mth.DEG_TO_RAD);
	}

	public static void setRadians(ModelPart part, float bend, float turn) {
		if (Math.abs(bend) < 1.0e-3f) {
			bend = 0f;
			turn = 0f;
		}
		((Part) (Object) part).freeflow$setBend(bend, turn);
		if (bend != 0f) {
			BENT.add(part);
		}
	}

	/** Straightens everything bent since the last call (after an entity has been drawn). */
	public static void straighten() {
		for (ModelPart part : BENT) {
			((Part) (Object) part).freeflow$setBend(0f, 0f);
		}
		BENT.clear();
	}

	// ------------------------------------------------------------------ the bend itself

	/**
	 * Moves {@code p} (part space, pixels) to where it is on the bent box [min, max]. Points
	 * above the middle cut stay, points below turn about the hinge, points on the cut go to
	 * the halfway plane, pushed out so both halves still meet them.
	 */
	public static void bendPoint(Vector3f p, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			float bend, float turn, int side) {
		if (bend == 0f || side < 0) {
			return;
		}
		float cx = (minX + maxX) * 0.5f;
		float cy = (minY + maxY) * 0.5f;
		float cz = (minZ + maxZ) * 0.5f;
		float ax = Mth.cos(turn);
		float az = Mth.sin(turn);
		float dx = p.x - cx;
		float dy = p.y - cy;
		float dz = p.z - cz;
		if (side > 0) {
			rotate(p, dx, dy, dz, ax, az, bend, 1f);
			p.add(cx, cy, cz);
			return;
		}
		float along = dx * ax + dz * az;
		float stretch = Math.min(MAX_JOINT_STRETCH, 1f / Math.max(1.0e-6f, Mth.cos(bend * 0.5f)));
		rotate(p, dx - along * ax, dy, dz - along * az, ax, az, bend * 0.5f, stretch);
		p.add(cx + along * ax, cy, cz + along * az);
	}

	/** Rodrigues rotation of (x, y, z) about the horizontal unit axis (ax, 0, az), then scaled. */
	private static void rotate(Vector3f out, float x, float y, float z, float ax, float az, float angle, float scale) {
		float cos = Mth.cos(angle);
		float sin = Mth.sin(angle);
		float dot = ax * x + az * z;
		// axis x v = (0*z - az*y, az*x - ax*z, ax*y - 0*x)
		float crossX = -az * y;
		float crossY = az * x - ax * z;
		float crossZ = ax * y;
		out.set((x * cos + crossX * sin + ax * dot * (1 - cos)) * scale,
				(y * cos + crossY * sin) * scale,
				(z * cos + crossZ * sin + az * dot * (1 - cos)) * scale);
	}

	/**
	 * Adds the lower half's turn to a pose stack that is already at the part (used for what the
	 * limb carries: the item in a hand follows the forearm).
	 */
	public static void applyLower(ModelPart part, PoseStack pose) {
		float bend = bend(part);
		if (bend == 0f) {
			return;
		}
		Quad[] quads = quads(part);
		if (quads.length == 0) {
			return;
		}
		Quad q = quads[0];
		float cx = (q.minX + q.maxX) / 32f;
		float cy = (q.minY + q.maxY) / 32f;
		float cz = (q.minZ + q.maxZ) / 32f;
		float turn = turn(part);
		pose.translate(cx, cy, cz);
		pose.mulPose(new Quaternionf().rotateAxis(bend, Mth.cos(turn), 0f, Mth.sin(turn)));
		pose.translate(-cx, -cy, -cz);
	}

	// ------------------------------------------------------------------ drawing

	/** One face of a box as vanilla would draw it, in part space (pixels), with its box's bounds. */
	private record Quad(float[] x, float[] y, float[] z, float[] u, float[] v, float nx, float ny, float nz,
			float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {}

	private static final Map<ModelPart, Quad[]> QUADS = new WeakHashMap<>();
	private static final PoseStack.Pose IDENTITY = new PoseStack().last();

	private static Quad[] quads(ModelPart part) {
		return QUADS.computeIfAbsent(part, BentLimbs::record);
	}

	/** Reads a part's own boxes back from vanilla's drawing code, so texture layout is never guessed. */
	private static Quad[] record(ModelPart part) {
		List<Quad> out = new ArrayList<>();
		part.visit(new PoseStack(), (pose, path, index, cube) -> {
			if (!path.isEmpty()) {
				return; // boxes of child parts are drawn by those parts
			}
			Recorder recorder = new Recorder();
			cube.compile(IDENTITY, recorder, 0, 0, -1);
			for (int i = 0; i + 3 < recorder.count; i += 4) {
				float[] x = new float[4];
				float[] y = new float[4];
				float[] z = new float[4];
				float[] u = new float[4];
				float[] v = new float[4];
				for (int k = 0; k < 4; k++) {
					x[k] = recorder.data.get((i + k) * 8) * 16f;
					y[k] = recorder.data.get((i + k) * 8 + 1) * 16f;
					z[k] = recorder.data.get((i + k) * 8 + 2) * 16f;
					u[k] = recorder.data.get((i + k) * 8 + 3);
					v[k] = recorder.data.get((i + k) * 8 + 4);
				}
				out.add(new Quad(x, y, z, u, v, recorder.data.get(i * 8 + 5), recorder.data.get(i * 8 + 6),
						recorder.data.get(i * 8 + 7), cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ));
			}
		});
		return out.toArray(new Quad[0]);
	}

	/** Draws the part's own boxes bent. The pose is already at the part (after translateAndRotate). */
	public static void draw(ModelPart part, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
		float bend = bend(part);
		float turn = turn(part);
		Emitter emitter = new Emitter(pose, consumer, light, overlay, color, bend, turn);
		for (Quad q : quads(part)) {
			emitter.quad(q);
		}
	}

	private static final class Emitter {
		private final Matrix4f matrix;
		private final PoseStack.Pose pose;
		private final VertexConsumer consumer;
		private final int light;
		private final int overlay;
		private final int color;
		private final float bend;
		private final float turn;
		private final Vector3f[] p = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
		private final float[] u = new float[4];
		private final float[] v = new float[4];
		private final Vector3f normal = new Vector3f();
		private final Vector3f edgeA = new Vector3f();
		private final Vector3f edgeB = new Vector3f();
		private final Vector3f scratch = new Vector3f();

		Emitter(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color, float bend, float turn) {
			this.pose = pose;
			this.matrix = pose.pose();
			this.consumer = consumer;
			this.light = light;
			this.overlay = overlay;
			this.color = color;
			this.bend = bend;
			this.turn = turn;
		}

		void quad(Quad q) {
			float mid = (q.minY + q.maxY) * 0.5f;
			int upper = 0;
			int lower = 0;
			for (int i = 0; i < 4; i++) {
				if (q.y[i] < mid - 1.0e-4f) {
					upper++;
				} else if (q.y[i] > mid + 1.0e-4f) {
					lower++;
				}
			}
			// Which way round this face is wound, so bent faces keep facing outwards.
			edgeA.set(q.x[1] - q.x[0], q.y[1] - q.y[0], q.z[1] - q.z[0]);
			edgeB.set(q.x[3] - q.x[0], q.y[3] - q.y[0], q.z[3] - q.z[0]);
			float winding = edgeA.cross(edgeB, scratch).dot(q.nx, q.ny, q.nz) >= 0f ? 1f : -1f;

			if (upper != 2 || lower != 2) {
				// An end cap (or a face that does not span the cut): moves as a whole.
				int side = lower > 0 ? 1 : -1;
				for (int i = 0; i < 4; i++) {
					corner(i, q, q.x[i], q.y[i], q.z[i], q.u[i], q.v[i], side);
				}
				flush(winding);
				return;
			}
			// A side face: split along the cut into an upper and a lower face.
			int first = 0;
			for (int i = 0; i < 4; i++) {
				if (q.y[i] < mid && q.y[(i + 3) % 4] > mid) {
					first = i;
				}
			}
			int ua = first;
			int ub = (first + 1) % 4;
			int lb = (first + 2) % 4;
			int la = (first + 3) % 4;
			float ka = (mid - q.y[ua]) / (q.y[la] - q.y[ua]);
			float kb = (mid - q.y[ub]) / (q.y[lb] - q.y[ub]);
			float max = Mth.lerp(ka, q.x[ua], q.x[la]);
			float maz = Mth.lerp(ka, q.z[ua], q.z[la]);
			float mau = Mth.lerp(ka, q.u[ua], q.u[la]);
			float mav = Mth.lerp(ka, q.v[ua], q.v[la]);
			float mbx = Mth.lerp(kb, q.x[ub], q.x[lb]);
			float mbz = Mth.lerp(kb, q.z[ub], q.z[lb]);
			float mbu = Mth.lerp(kb, q.u[ub], q.u[lb]);
			float mbv = Mth.lerp(kb, q.v[ub], q.v[lb]);

			corner(0, q, q.x[ua], q.y[ua], q.z[ua], q.u[ua], q.v[ua], -1);
			corner(1, q, q.x[ub], q.y[ub], q.z[ub], q.u[ub], q.v[ub], -1);
			corner(2, q, mbx, mid, mbz, mbu, mbv, 0);
			corner(3, q, max, mid, maz, mau, mav, 0);
			flush(winding);

			corner(0, q, max, mid, maz, mau, mav, 0);
			corner(1, q, mbx, mid, mbz, mbu, mbv, 0);
			corner(2, q, q.x[lb], q.y[lb], q.z[lb], q.u[lb], q.v[lb], 1);
			corner(3, q, q.x[la], q.y[la], q.z[la], q.u[la], q.v[la], 1);
			flush(winding);
		}

		private void corner(int i, Quad q, float x, float y, float z, float uu, float vv, int side) {
			p[i].set(x, y, z);
			bendPoint(p[i], q.minX, q.minY, q.minZ, q.maxX, q.maxY, q.maxZ, bend, turn, side);
			u[i] = uu;
			v[i] = vv;
		}

		private void flush(float winding) {
			p[1].sub(p[0], edgeA);
			p[3].sub(p[0], edgeB);
			edgeA.cross(edgeB, normal);
			if (normal.lengthSquared() < 1.0e-10f) {
				p[2].sub(p[0], edgeB);
				edgeA.cross(edgeB, normal);
			}
			if (normal.lengthSquared() < 1.0e-10f) {
				return; // collapsed to nothing
			}
			normal.normalize().mul(winding);
			pose.transformNormal(normal.x, normal.y, normal.z, scratch);
			float nx = scratch.x;
			float ny = scratch.y;
			float nz = scratch.z;
			for (int i = 0; i < 4; i++) {
				matrix.transformPosition(p[i].x / 16f, p[i].y / 16f, p[i].z / 16f, scratch);
				consumer.addVertex(scratch.x, scratch.y, scratch.z, color, u[i], v[i], overlay, light, nx, ny, nz);
			}
		}
	}

	/** Catches the vertices vanilla emits for a box: x, y, z, u, v, normal per vertex. */
	private static final class Recorder implements VertexConsumer {
		final it.unimi.dsi.fastutil.floats.FloatArrayList data = new it.unimi.dsi.fastutil.floats.FloatArrayList();
		int count = 0;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			data.add(x);
			data.add(y);
			data.add(z);
			data.add(0f);
			data.add(0f);
			data.add(0f);
			data.add(0f);
			data.add(0f);
			count++;
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			int at = (count - 1) * 8;
			data.set(at + 3, u);
			data.set(at + 4, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			int at = (count - 1) * 8;
			data.set(at + 5, x);
			data.set(at + 6, y);
			data.set(at + 7, z);
			return this;
		}
	}
}

package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.network.AnimationPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Plays the combat animations (assets/freeflow/player_animations) on players through two
 * playerAnimator layers added to every client-side player:
 * <ul>
 *   <li>a locomotion layer that holds the fighter in a guard stance while a fight is on
 *       (standing, advancing, sprinting), so moves start from and return to a stance;</li>
 *   <li>the combat layer above it for strikes, counters, evades and takedowns. A blow's clip
 *       is started part-way in when the hit is due sooner than the clip's own impact tick, so
 *       the fist arrives on the tick the server lands the hit.</li>
 * </ul>
 */
public final class CombatAnimations {
	private CombatAnimations() {}

	private static final ResourceLocation LAYER_ID = FreeflowMod.id("combat");
	/** Above vanilla, below emote mods' usual priorities. */
	private static final int LAYER_PRIORITY = 1000;

	/** Looping stance/locomotion sits below the combat moves, which override it. */
	private static final ResourceLocation LOCOMOTION_LAYER_ID = FreeflowMod.id("locomotion");
	private static final int LOCOMOTION_LAYER_PRIORITY = 900;

	/** Other players count as fighting for this long after their last combat move. */
	private static final int REMOTE_COMBAT_TICKS = 100;

	/** Locomotion clip currently playing per player entity id ("" = none). */
	private static final Map<Integer, String> LOCOMOTION = new HashMap<>();
	/** Client tick of each player's last combat move. */
	private static final Map<Integer, Long> LAST_MOVE = new HashMap<>();
	/** Ticks each player has been off the ground (steps and small drops must not break the stance). */
	private static final Map<Integer, Integer> AIR_TICKS = new HashMap<>();
	private static final int AIR_GRACE_TICKS = 5;

	/** Developer preview: treat the local player as being in a fight. */
	public static boolean previewFighting = false;

	/** Tick on which each clip's blow lands (assets/freeflow/animation_meta.json). */
	private static Map<String, Integer> impactTicks;

	public static void init() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			stack.addAnimLayer(LAYER_PRIORITY, layer);
			PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);

			ModifierLayer<IAnimation> locomotion = new ModifierLayer<>();
			stack.addAnimLayer(LOCOMOTION_LAYER_PRIORITY, locomotion);
			PlayerAnimationAccess.getPlayerAssociatedData(player).set(LOCOMOTION_LAYER_ID, locomotion);
		});
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player, ResourceLocation id) {
		IAnimation data = PlayerAnimationAccess.getPlayerAssociatedData(player).get(id);
		return data instanceof ModifierLayer<?> layer ? (ModifierLayer<IAnimation>) layer : null;
	}

	// ------------------------------------------------------------------ stance / locomotion

	/** Keeps every visible fighter in the right stance clip: guard, advancing or sprinting. */
	public static void tickLocomotion(Minecraft mc) {
		if (mc.level == null) {
			LOCOMOTION.clear();
			LAST_MOVE.clear();
			AIR_TICKS.clear();
			return;
		}
		for (AbstractClientPlayer player : mc.level.players()) {
			String wanted = locomotionFor(mc, player);
			String current = LOCOMOTION.getOrDefault(player.getId(), "");
			if (wanted.equals(current)) {
				continue;
			}
			ModifierLayer<IAnimation> layer = layer(player, LOCOMOTION_LAYER_ID);
			if (layer == null) {
				continue;
			}
			KeyframeAnimation clip = wanted.isEmpty() ? null : find(wanted);
			if (!wanted.isEmpty() && clip == null) {
				continue;
			}
			LOCOMOTION.put(player.getId(), wanted);
			layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE),
					clip == null ? null : new KeyframeAnimationPlayer(clip), true);
		}
		LOCOMOTION.keySet().removeIf(id -> mc.level.getEntity(id) == null);
		LAST_MOVE.keySet().removeIf(id -> mc.level.getEntity(id) == null);
		AIR_TICKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
	}

	private static String locomotionFor(Minecraft mc, AbstractClientPlayer player) {
		int air = player.onGround() ? 0 : AIR_TICKS.getOrDefault(player.getId(), 0) + 1;
		AIR_TICKS.put(player.getId(), air);
		boolean fighting = player == mc.player
				? ClientCombatState.isFreeflowActive() || previewFighting
				: ClientCombo.clientTick - LAST_MOVE.getOrDefault(player.getId(), -1000L) < REMOTE_COMBAT_TICKS;
		if (!fighting || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isInWater()
				|| player.getPose() != Pose.STANDING || air > AIR_GRACE_TICKS) {
			return "";
		}
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		double speed = Math.sqrt(dx * dx + dz * dz);
		if (speed < 0.02) {
			return Anim.STANCE;
		}
		// The stride clips step forwards: only use them when the body really is travelling the
		// way it faces. Side-steps and back-pedalling keep the guard and shuffle the feet.
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double ahead = (-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz) / speed;
		if (ahead < 0.6) {
			return Anim.SHUFFLE;
		}
		return player.isSprinting() ? Anim.RUN : Anim.ADVANCE;
	}

	// ------------------------------------------------------------------ combat moves

	/** True while a combat move is playing on this player (its body is being posed by us). */
	public static boolean isAnimating(AbstractClientPlayer player) {
		ModifierLayer<IAnimation> layer = layer(player, LAYER_ID);
		if (layer != null && layer.isActive()) {
			return true;
		}
		return !LOCOMOTION.getOrDefault(player.getId(), "").isEmpty();
	}

	/**
	 * Elbows and knees of a player: the animation files carry them as four extra bones
	 * (rotation = hinge rotation, hinge turn), blended by playerAnimator like every other bone.
	 * Called after the model has been posed, for every player drawn.
	 */
	public static void applyBends(AbstractClientPlayer player, EntityModel<?> model) {
		if (!(model instanceof PlayerModel<?> m)) {
			return;
		}
		AnimationApplier animation = ((IAnimatedPlayer) player).playerAnimator_getAnimation();
		if (!animation.isActive()) {
			return;
		}
		bend(animation, "rightElbow", m.rightArm, m.rightSleeve);
		bend(animation, "leftElbow", m.leftArm, m.leftSleeve);
		bend(animation, "rightKnee", m.rightLeg, m.rightPants);
		bend(animation, "leftKnee", m.leftLeg, m.leftPants);
	}

	private static void bend(AnimationApplier animation, String bone, ModelPart limb, ModelPart outerLayer) {
		Vec3f joint = animation.get3DTransform(bone, TransformType.ROTATION, Vec3f.ZERO);
		BentLimbs.setRadians(limb, joint.getX(), joint.getY());
		BentLimbs.setRadians(outerLayer, joint.getX(), joint.getY());
	}

	/** Developer preview diagnostics: the local player's elbow and knee bends, degrees. */
	public static String debugBends() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return "-";
		}
		AnimationApplier animation = ((IAnimatedPlayer) mc.player).playerAnimator_getAnimation();
		StringBuilder out = new StringBuilder();
		for (String bone : new String[] {"rightElbow", "leftElbow", "rightKnee", "leftKnee"}) {
			Vec3f joint = animation.get3DTransform(bone, TransformType.ROTATION, Vec3f.ZERO);
			out.append(String.format(java.util.Locale.ROOT, "%s %.1f ", bone, joint.getX() * Mth.RAD_TO_DEG));
		}
		return out.toString().trim();
	}

	/** Developer preview diagnostics: is the local player's combat layer animating? */
	public static String debugState() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return "no player";
		}
		ModifierLayer<IAnimation> layer = layer(mc.player, LAYER_ID);
		if (layer == null) {
			return "no layer";
		}
		IAnimation anim = layer.getAnimation();
		return "combat layer: " + (anim == null ? "empty" : (anim.isActive() ? "active" : "finished"));
	}

	/** Plays an animation on the local player from its first tick (developer preview). */
	public static void playLocal(String name) {
		playLocal(name, AnimationPayload.FROM_START);
	}

	/** Plays a blow on the local player as the game does: its hit {@code lead} ticks away. */
	public static void playLocal(String name, int lead) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			onAnimation(new AnimationPayload(mc.player.getId(), name, lead));
		}
	}

	public static void onAnimation(AnimationPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity entity = mc.level.getEntity(payload.entityId());
		if (!(entity instanceof AbstractClientPlayer player)) {
			return;
		}
		ModifierLayer<IAnimation> layer = layer(player, LAYER_ID);
		if (layer == null) {
			return;
		}
		if (payload.name().equals(AnimationPayload.STOP)) {
			layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE), null);
			return;
		}
		KeyframeAnimation animation = find(payload.name());
		if (animation == null) {
			FreeflowMod.LOGGER.warn("Missing combat animation {}", payload.name());
			return;
		}
		LAST_MOVE.put(player.getId(), ClientCombo.clientTick);
		layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(1, Ease.OUTQUAD),
				new KeyframeAnimationPlayer(animation, startTick(payload)), true);
	}

	/**
	 * Where in the clip to start so its blow lands when the server's hit does: a clip whose
	 * impact is on tick 7 starts on tick 4 if the hit is only 3 ticks away.
	 */
	private static int startTick(AnimationPayload payload) {
		if (payload.lead() < 0) {
			return 0;
		}
		Integer impact = impactTicks().get(payload.name());
		return impact == null ? 0 : Math.max(0, impact - payload.lead());
	}

	@Nullable
	private static KeyframeAnimation find(String name) {
		return PlayerAnimationRegistry.getAnimation(FreeflowMod.id(name)) instanceof KeyframeAnimation k ? k : null;
	}

	private static Map<String, Integer> impactTicks() {
		if (impactTicks == null) {
			Map<String, Integer> out = new HashMap<>();
			try (InputStream in = CombatAnimations.class.getResourceAsStream("/assets/freeflow/animation_meta.json")) {
				if (in != null) {
					JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : root.entrySet()) {
						out.put(e.getKey(), e.getValue().getAsJsonObject().get("impact").getAsInt());
					}
				}
			} catch (Exception ex) {
				FreeflowMod.LOGGER.error("Failed to load animation_meta.json", ex);
			}
			impactTicks = out;
		}
		return impactTicks;
	}
}

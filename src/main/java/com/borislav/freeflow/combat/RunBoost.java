package com.borislav.freeflow.combat;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Running in a fight is faster than a plain Minecraft sprint (Arkham's run covers ground quickly).
 * A temporary speed modifier while the player sprints in freeflow; the server owns it, so the
 * client moves with it and nobody can give themselves more.
 */
public final class RunBoost {
	private RunBoost() {}

	public static final ResourceLocation ID = FreeflowMod.id("fight_run");

	public static void update(ServerPlayer player, boolean freeflow) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean want = freeflow && player.isSprinting() && FreeflowConfig.runSpeedBonus > 0;
		AttributeModifier current = speed.getModifier(ID);
		if (!want) {
			if (current != null) {
				speed.removeModifier(ID);
			}
			return;
		}
		if (current == null || current.amount() != FreeflowConfig.runSpeedBonus) {
			speed.removeModifier(ID);
			speed.addTransientModifier(new AttributeModifier(ID, FreeflowConfig.runSpeedBonus,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}

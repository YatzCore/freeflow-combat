package com.borislav.freeflow.mixin;

import com.borislav.freeflow.ai.CombatDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slimes (and magma cubes) hurt by touch, bypassing attack goals. Against a freeflow player
 * that touch becomes a director turn request, so they get a warning and can be countered.
 */
@Mixin(Slime.class)
public abstract class SlimeMixin {
	@Inject(method = "dealDamage", at = @At("HEAD"), cancellable = true)
	private void freeflow$requestTurn(LivingEntity target, CallbackInfo ci) {
		if (!CombatDirector.performing && target instanceof ServerPlayer player && CombatDirector.isDirected(player)) {
			CombatDirector.requestAttack(player, (Mob) (Object) this);
			ci.cancel();
		}
	}
}

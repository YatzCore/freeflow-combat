package com.borislav.freeflow.mixin;

import com.borislav.freeflow.ai.CombatDirector;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While freeflow is active, picked-up items never go into the selected (empty fist) hotbar
 * slot, which would switch freeflow off mid-fight. They go to the next free slot instead.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Shadow
	public int selected;

	@Shadow
	@Final
	public Player player;

	@Shadow
	@Final
	public NonNullList<ItemStack> items;

	@Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
	private void freeflow$keepFistEmpty(CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValueI() != selected || !(player instanceof ServerPlayer serverPlayer)
				|| !CombatDirector.isDirected(serverPlayer)) {
			return;
		}
		for (int i = 0; i < items.size(); i++) {
			if (i != selected && items.get(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1); // only the fist slot is free: leave the item on the ground
	}
}

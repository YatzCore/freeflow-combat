package com.borislav.freeflow.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the controller "press" vanilla keys once (inventory, drop) the way a key event would. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int freeflow$getClickCount();

	@Accessor("clickCount")
	void freeflow$setClickCount(int count);
}

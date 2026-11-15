package com.borislav.freeflow.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read/reset a creeper's fuse (for the fuse telegraph and the kick-away counter). */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("swell")
	int freeflow$getSwell();

	@Accessor("swell")
	void freeflow$setSwell(int swell);

	@Accessor("oldSwell")
	void freeflow$setOldSwell(int swell);

	@Accessor("maxSwell")
	int freeflow$getMaxSwell();
}

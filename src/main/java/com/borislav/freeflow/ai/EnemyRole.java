package com.borislav.freeflow.ai;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

/**
 * How an enemy fights, which decides how the player has to deal with it.
 * Resolved from entity-type tags ({@code data/freeflow/tags/entity_type/*.json},
 * extendable by datapacks) plus what the mob is holding.
 */
public enum EnemyRole {
	/** Counterable melee. */
	NORMAL,
	/** Strikes from the front bounce off: stun it or hit it from behind (vault over). */
	SHIELDED,
	/** Attacks can't be countered (red warning): evade them. */
	ARMED,
	/** Shoots from range; the shot is telegraphed and can be deflected with a counter. */
	RANGED,
	/** Big enemies: immune to takedowns and knockdowns, short stuns, shorter snap range. */
	BOSS;

	public static final TagKey<EntityType<?>> TAG_SHIELDED = tag("shielded");
	public static final TagKey<EntityType<?>> TAG_ARMED = tag("armed");
	public static final TagKey<EntityType<?>> TAG_RANGED = tag("ranged");
	public static final TagKey<EntityType<?>> TAG_BOSS = tag("boss");

	private static TagKey<EntityType<?>> tag(String name) {
		return TagKey.create(Registries.ENTITY_TYPE, FreeflowMod.id(name));
	}

	public static EnemyRole of(Mob mob) {
		EntityType<?> type = mob.getType();
		if (type.is(TAG_BOSS) || mob.getMaxHealth() > FreeflowConfig.bossHealthThreshold) {
			return BOSS;
		}
		if (type.is(TAG_SHIELDED) || mob.getMainHandItem().is(Items.SHIELD) || mob.getOffhandItem().is(Items.SHIELD)) {
			return SHIELDED;
		}
		Item main = mob.getMainHandItem().getItem();
		if (type.is(TAG_RANGED)
				|| (mob instanceof RangedAttackMob && (main instanceof BowItem || main instanceof CrossbowItem))) {
			return RANGED;
		}
		if (type.is(TAG_ARMED)
				|| (FreeflowConfig.armedFromHeldWeapons && (main instanceof SwordItem || main instanceof AxeItem))) {
			return ARMED;
		}
		return NORMAL;
	}

	/** Telegraph kind sent to the client (decides the warning colour). */
	public int telegraphKind() {
		return switch (this) {
			case ARMED -> 1;
			case RANGED -> 2;
			default -> 0;
		};
	}
}

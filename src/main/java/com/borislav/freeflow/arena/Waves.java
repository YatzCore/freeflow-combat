package com.borislav.freeflow.arena;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The combat challenge: four waves that each bring in one more kind of enemy, the way the Arkham
 * training challenges build up. Thugs are husks (they don't burn in daylight).
 */
public final class Waves {
	private Waves() {}

	/** One enemy to spawn: its type and what it carries (which decides its role). */
	public record Enemy(EntityType<? extends Mob> type, Gear gear) {}

	public enum Gear {
		/** Bare hands: counter it (blue !). */
		NONE,
		/** Shield: stun it or hit it from behind. */
		SHIELD,
		/** Sword: its attacks can't be countered (red !): roll. */
		SWORD,
		/** Helmet only (a skeleton that mustn't burn). */
		HELMET
	}

	public record Wave(String name, List<Enemy> enemies) {}

	public static final List<Wave> ALL = List.of(
			new Wave("Warm-up", list(thugs(4))),
			new Wave("Shields", list(thugs(4), of(EntityType.HUSK, Gear.SHIELD, 2))),
			new Wave("Weapons", list(thugs(4), of(EntityType.HUSK, Gear.SHIELD, 1), of(EntityType.VINDICATOR, Gear.NONE, 2),
					of(EntityType.SKELETON, Gear.HELMET, 1), of(EntityType.PILLAGER, Gear.NONE, 1))),
			new Wave("Everything", list(thugs(5), of(EntityType.HUSK, Gear.SHIELD, 2), of(EntityType.HUSK, Gear.SWORD, 1),
					of(EntityType.VINDICATOR, Gear.NONE, 2), of(EntityType.PILLAGER, Gear.NONE, 2),
					of(EntityType.RAVAGER, Gear.NONE, 1))));

	private static List<Enemy> thugs(int n) {
		return of(EntityType.HUSK, Gear.NONE, n);
	}

	private static List<Enemy> of(EntityType<? extends Mob> type, Gear gear, int n) {
		List<Enemy> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new Enemy(type, gear));
		}
		return out;
	}

	@SafeVarargs
	private static List<Enemy> list(List<Enemy>... groups) {
		List<Enemy> out = new ArrayList<>();
		for (List<Enemy> g : groups) {
			out.addAll(g);
		}
		return List.copyOf(out);
	}

	public static void equip(Mob mob, Gear gear) {
		switch (gear) {
			case SHIELD -> mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			case SWORD -> mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			case HELMET -> mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			case NONE -> {}
		}
	}
}

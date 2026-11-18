package com.borislav.freeflow.arena;

import net.minecraft.util.RandomSource;
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

	/** {@code healthScale} multiplies every enemy's max health (infinite mode's later waves). */
	public record Wave(String name, List<Enemy> enemies, double healthScale) {
		public Wave(String name, List<Enemy> enemies) {
			this(name, enemies, 1.0);
		}
	}

	/** Most enemies an infinite wave sends at once; after that they get tougher instead. */
	public static final int INFINITE_MAX_ENEMIES = 14;
	/** Infinite waves past this one give enemies more health (+10% per wave, up to 3x). */
	public static final int INFINITE_TOUGHER_FROM = 10;
	private static final int MAX_SHOOTERS = 3;

	/**
	 * Infinite mode, wave {@code n} (1-based): one more enemy each wave up to
	 * {@link #INFINITE_MAX_ENEMIES}, new kinds join as the waves go on (shields at 2, armed
	 * fighters and skeletons at 3, crossbows at 4, swords at 5), every 5th wave brings ravagers,
	 * and past wave {@link #INFINITE_TOUGHER_FROM} everyone has more health.
	 */
	public static Wave infinite(int n, RandomSource random) {
		int total = Math.min(3 + n, INFINITE_MAX_ENEMIES);
		boolean bossWave = n % 5 == 0;
		int ravagers = bossWave ? Math.min(3, n / 5) : n >= 12 ? 1 : 0;
		// At least 3 plain thugs, so there's always something to counter and build a combo on.
		int specials = Math.max(0, Math.min(total - 3 - ravagers, n - 1));
		List<Enemy> out = new ArrayList<>();
		for (int i = 0; i < ravagers; i++) {
			out.add(new Enemy(EntityType.RAVAGER, Gear.NONE));
		}
		List<Enemy> pool = new ArrayList<>();
		if (n >= 2) pool.add(new Enemy(EntityType.HUSK, Gear.SHIELD));
		if (n >= 3) pool.add(new Enemy(EntityType.VINDICATOR, Gear.NONE));
		if (n >= 3) pool.add(new Enemy(EntityType.SKELETON, Gear.HELMET));
		if (n >= 4) pool.add(new Enemy(EntityType.PILLAGER, Gear.NONE));
		if (n >= 5) pool.add(new Enemy(EntityType.HUSK, Gear.SWORD));
		int shooters = 0;
		for (int i = 0; i < specials && !pool.isEmpty(); i++) {
			Enemy e = pool.get(random.nextInt(pool.size()));
			if (isShooter(e)) {
				if (shooters >= MAX_SHOOTERS) {
					e = new Enemy(EntityType.HUSK, n >= 5 ? Gear.SWORD : Gear.SHIELD);
				} else {
					shooters++;
				}
			}
			out.add(e);
		}
		while (out.size() < total) {
			out.add(new Enemy(EntityType.HUSK, Gear.NONE));
		}
		java.util.Collections.shuffle(out, new java.util.Random(random.nextLong()));
		double scale = n > INFINITE_TOUGHER_FROM ? Math.min(3.0, 1.0 + 0.1 * (n - INFINITE_TOUGHER_FROM)) : 1.0;
		String name = bossWave ? "Boss wave" : total + " enemies" + (scale > 1.0 ? ", tougher" : "");
		return new Wave(name, List.copyOf(out), scale);
	}

	public static boolean isShooter(Enemy e) {
		return e.type() == EntityType.SKELETON || e.type() == EntityType.PILLAGER;
	}

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

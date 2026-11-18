package com.borislav.freeflow.arena;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.CombatStateManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combat challenge arena ({@code /freeflow arena start}). Built in the sky above the player, so it
 * never touches anything they built; when the challenge ends (won, lost or stopped) the player goes
 * back where they were and the arena, its enemies and their drops are removed.
 * <p>
 * Losing doesn't kill you: at zero health the challenge ends, as a training room should.
 */
public final class Arena {

	/** Floor radius (blocks); the wall stands just outside it. */
	public static final int RADIUS = 13;
	public static final int WALL_HEIGHT = 5;
	/** Space kept clear above the floor while checking the spot is empty. */
	private static final int HEADROOM = 8;
	/** Enemies come in at this distance from the centre. */
	private static final double SPAWN_RADIUS = 10.5;
	private static final int COUNTDOWN_TICKS = 60;
	private static final int SPAWN_GAP_TICKS = 6;
	private static final int END_TICKS = 100;

	/** Heal between waves (half a heart = 1). */
	private static final float HEAL_BETWEEN_WAVES = 6.0f;
	/** After this long in a wave, the last few enemies glow so you can find them. */
	private static final int GLOW_AFTER_TICKS = 200;
	private static final int GLOW_WHEN_LEFT = 2;

	private static final net.minecraft.resources.ResourceLocation TOUGHER =
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("freeflow", "arena_tougher");

	public static final String ARENA_TAG = "freeflow_arena";

	private static final Map<UUID, Arena> ACTIVE = new HashMap<>();

	public enum Mode {
		/** The four set waves. */
		CLASSIC,
		/** Endless waves that keep growing until you're knocked out. */
		INFINITE
	}

	private enum Phase { COUNTDOWN, SPAWNING, FIGHTING, ENDING }

	private final UUID owner;
	private final Mode mode;
	private final ServerLevel level;
	private final BlockPos center;
	private final ResourceKey<Level> returnDimension;
	private final Vec3 returnPos;
	private final float returnYaw;
	private final float returnPitch;
	private final List<BlockPos> placed = new ArrayList<>();
	private final List<UUID> enemies = new ArrayList<>();
	private final Deque<Waves.Enemy> toSpawn = new ArrayDeque<>();
	private Phase phase = Phase.COUNTDOWN;
	private int wave = 0;
	private int timer = COUNTDOWN_TICKS;
	private int spawned = 0;
	private long startTick;
	/** The wave being fought (infinite waves are rolled when their countdown starts). */
	private Waves.Wave current;
	private int phaseTicks = 0;
	private int knockouts = 0;
	private int maxCombo = 0;
	private boolean newRecord = false;

	private Arena(ServerPlayer player, BlockPos center, Mode mode) {
		this.owner = player.getUUID();
		this.mode = mode;
		this.level = player.serverLevel();
		this.center = center;
		this.returnDimension = player.level().dimension();
		this.returnPos = player.position();
		this.returnYaw = player.getYRot();
		this.returnPitch = player.getXRot();
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Arena::tickAll);
		// Never leave an arena floating in the sky after a shutdown.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Arena arena : List.copyOf(ACTIVE.values())) {
				arena.close(server.getPlayerList().getPlayer(arena.owner));
			}
		});
		// A knockout in the arena ends the challenge instead of killing you.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID())) {
				player.setHealth(player.getMaxHealth());
				player.clearFire();
				ACTIVE.get(player.getUUID()).finish(player, false);
				return false;
			}
			return true;
		});
		// Arena enemies that die (not removed) are knock-outs for the run's result.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob && entity.getTags().contains(ARENA_TAG)) {
				for (Arena arena : ACTIVE.values()) {
					if (arena.enemies.contains(entity.getUUID())) {
						arena.knockouts++;
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Arena arena = ACTIVE.get(handler.getPlayer().getUUID());
			if (arena != null) {
				arena.close(handler.getPlayer());
			}
		});
	}

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	// ------------------------------------------------------------------ start / stop

	/** Builds the arena above the player and starts wave 1. Returns an error message, or null. */
	@Nullable
	public static Component start(ServerPlayer player, Mode mode) {
		return start(player, mode, false);
	}

	/** Test hook: {@code anyDifficulty} skips the Peaceful check (the GameTest world is Peaceful). */
	@Nullable
	public static Component start(ServerPlayer player, boolean anyDifficulty) {
		return start(player, Mode.CLASSIC, anyDifficulty);
	}

	@Nullable
	public static Component start(ServerPlayer player, Mode mode, boolean anyDifficulty) {
		if (ACTIVE.containsKey(player.getUUID())) {
			return Component.literal("You're already in an arena: /freeflow arena stop ends it.");
		}
		ServerLevel level = player.serverLevel();
		if (!anyDifficulty && level.getDifficulty() == Difficulty.PEACEFUL) {
			return Component.literal("Enemies can't spawn on Peaceful: set the difficulty to Easy or higher.");
		}
		// The first empty layer of sky from height 200 (always well above you), going up.
		BlockPos center = null;
		int top = level.getMaxBuildHeight() - HEADROOM - 2;
		for (int y = Math.max(Math.min(200, top), player.getBlockY() + 20); y <= top; y += HEADROOM + 4) {
			BlockPos c = new BlockPos(player.getBlockX(), y, player.getBlockZ());
			if (isClear(level, c)) {
				center = c;
				break;
			}
		}
		if (center == null) {
			return Component.literal("No empty sky above you to build the arena in; move a little and try again.");
		}
		Arena arena = new Arena(player, center, mode);
		arena.build();
		ACTIVE.put(player.getUUID(), arena);
		arena.startTick = level.getGameTime();
		player.teleportTo(level, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, player.getYRot(), 0);
		player.fallDistance = 0;
		CombatState state = CombatStateManager.get(player);
		state.combo = 0;
		if (mode == Mode.INFINITE) {
			ArenaRecords.Entry best = ArenaRecords.get(level.getServer()).of(player.getUUID());
			title(player, Component.literal("Infinite Challenge").withStyle(ChatFormatting.GOLD),
					Component.literal(best.infiniteWaves > 0 ? "Your best: " + best.infiniteWaves + " waves" : "How long can you last?"));
		} else {
			title(player, Component.literal("Combat Challenge").withStyle(ChatFormatting.GOLD),
					Component.literal(Waves.ALL.size() + " waves. Empty hand, fists only."));
		}
		FreeflowMod.LOGGER.info("{} started the {} combat challenge at {}", player.getName().getString(),
				mode.name().toLowerCase(java.util.Locale.ROOT), center);
		return null;
	}

	/** Ends the player's challenge early. Returns false if they had none. */
	public static boolean stop(ServerPlayer player) {
		Arena arena = ACTIVE.get(player.getUUID());
		if (arena == null) {
			return false;
		}
		if (arena.mode == Mode.INFINITE && arena.phase != Phase.ENDING) {
			arena.recordInfinite(player); // the waves you cleared still count
		}
		arena.close(player);
		return true;
	}

	// ------------------------------------------------------------------ building

	private static boolean isClear(ServerLevel level, BlockPos center) {
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS - 2, -1, -RADIUS - 2),
				center.offset(RADIUS + 2, HEADROOM, RADIUS + 2))) {
			if (!level.getBlockState(p).isAir()) {
				return false;
			}
		}
		return true;
	}

	private void build() {
		int r2 = RADIUS * RADIUS;
		int wall2 = (RADIUS + 1) * (RADIUS + 1);
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
			for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > wall2 + RADIUS) {
					continue;
				}
				BlockPos floor = center.offset(dx, 0, dz);
				boolean wall = d2 > r2;
				set(floor, floorBlock(dx, dz, d2, wall));
				set(floor.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				if (wall) {
					for (int h = 1; h <= WALL_HEIGHT; h++) {
						// Lamps in the wall at the 8 compass points.
						boolean lamp = h == 3 && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
						set(floor.above(h), lamp ? Blocks.SEA_LANTERN.defaultBlockState()
								: h == WALL_HEIGHT ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
								: Blocks.DEEPSLATE_TILES.defaultBlockState());
					}
				}
			}
		}
	}

	/** Dark tiled floor with a ring and a centre mark, lit from below by sea lanterns. */
	private static BlockState floorBlock(int dx, int dz, int d2, boolean wall) {
		if (wall) {
			return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		}
		double d = Math.sqrt(d2);
		if (d < 1.5) {
			return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
		}
		if (Math.abs(d - 7.5) < 0.5) {
			return (dx + dz) % 4 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		if (Math.abs(d - RADIUS + 0.5) < 0.5 && (Math.abs(dx) == 0 || Math.abs(dz) == 0 || Math.abs(dx) == Math.abs(dz))) {
			return Blocks.SEA_LANTERN.defaultBlockState();
		}
		return ((dx + dz) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
	}

	private void set(BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		placed.add(pos.immutable());
	}

	private void demolish() {
		for (BlockPos p : placed) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		placed.clear();
	}

	// ------------------------------------------------------------------ waves

	private static void tickAll(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Arena arena : List.copyOf(ACTIVE.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(arena.owner);
			if (player == null) {
				arena.close(null);
			} else {
				arena.tick(player);
			}
		}
	}

	private void tick(ServerPlayer player) {
		if (player.level() != level || player.position().distanceTo(Vec3.atBottomCenterOf(center)) > RADIUS * 3) {
			FreeflowMod.LOGGER.info("{} left the arena; challenge ended", player.getName().getString());
			close(player); // left the arena (teleported, changed dimension)
			return;
		}
		maxCombo = Math.max(maxCombo, CombatStateManager.get(player).combo);
		phaseTicks++;
		switch (phase) {
			case COUNTDOWN -> {
				if (timer == COUNTDOWN_TICKS) {
					current = mode == Mode.INFINITE ? Waves.infinite(wave + 1, level.getRandom()) : Waves.ALL.get(wave);
					boolean boss = mode == Mode.INFINITE && (wave + 1) % 5 == 0;
					String label = mode == Mode.INFINITE ? "Wave " + (wave + 1) : "Wave " + (wave + 1) + " / " + Waves.ALL.size();
					title(player, Component.literal(label).withStyle(boss ? ChatFormatting.RED : ChatFormatting.GOLD),
							Component.literal(current.name()));
				}
				if (--timer <= 0) {
					toSpawn.clear();
					toSpawn.addAll(current.enemies());
					spawned = 0;
					phase = Phase.SPAWNING;
					timer = 0;
					level.playSound(null, center.above(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 1.0f);
				}
			}
			case SPAWNING -> {
				if (player.tickCount % 10 == 0) {
					status(player, aliveEnemies() + toSpawn.size());
				}
				if (--timer <= 0) {
					Waves.Enemy next = toSpawn.poll();
					if (next != null) {
						spawn(player, next, spawned++);
					}
					timer = SPAWN_GAP_TICKS;
					if (toSpawn.isEmpty()) {
						phase = Phase.FIGHTING;
						phaseTicks = 0;
					}
				}
			}
			case FIGHTING -> {
				if (player.tickCount % 10 != 0) {
					break;
				}
				int alive = aliveEnemies();
				if (alive == 0) {
					sweepDrops();
					wave++;
					if (mode == Mode.CLASSIC && wave >= Waves.ALL.size()) {
						finish(player, true);
					} else {
						player.heal(HEAL_BETWEEN_WAVES);
						level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
						phase = Phase.COUNTDOWN;
						timer = COUNTDOWN_TICKS;
					}
				} else {
					status(player, alive);
					if (phaseTicks > GLOW_AFTER_TICKS && alive <= GLOW_WHEN_LEFT) {
						glowStragglers();
					}
				}
			}
			case ENDING -> {
				if (--timer <= 0) {
					close(player);
				}
			}
		}
	}

	private void spawn(ServerPlayer player, Waves.Enemy enemy, int index) {
		int total = current.enemies().size();
		double angle = Math.PI * 2 * index / total + wave * 0.6;
		Vec3 at = new Vec3(center.getX() + 0.5 + Math.cos(angle) * SPAWN_RADIUS, center.getY() + 1,
				center.getZ() + 0.5 + Math.sin(angle) * SPAWN_RADIUS);
		Mob mob = enemy.type().create(level);
		if (mob == null) {
			return;
		}
		float yaw = (float) (Mth.atan2(center.getZ() + 0.5 - at.z, center.getX() + 0.5 - at.x) * Mth.RAD_TO_DEG) - 90f;
		mob.moveTo(at.x, at.y, at.z, yaw, 0);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);
		if (mob instanceof Zombie zombie) {
			zombie.setBaby(false);
		}
		Waves.equip(mob, enemy.gear());
		if (current.healthScale() > 1.0) {
			AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.addPermanentModifier(new AttributeModifier(TOUGHER, current.healthScale() - 1.0,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				mob.setHealth(mob.getMaxHealth());
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0f);
		}
		// No loot in a training room (equipment drops are off above; this is the mob's own loot table).
		net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
		mob.addAdditionalSaveData(data);
		data.putString("DeathLootTable", "minecraft:empty");
		mob.readAdditionalSaveData(data);
		mob.setPersistenceRequired();
		mob.addTag(ARENA_TAG);
		level.addFreshEntityWithPassengers(mob);
		if (mob.getVehicle() != null) {
			mob.getVehicle().discard(); // no chicken jockeys in a training room
		}
		mob.setTarget(player);
		enemies.add(mob.getUUID());
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8, at.z, 20, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8f, 0.8f);
	}

	private int aliveEnemies() {
		int n = 0;
		AABB inside = new AABB(center).inflate(RADIUS + 4, HEADROOM + 4, RADIUS + 4);
		for (UUID id : enemies) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				if (!inside.contains(e.position())) {
					e.discard(); // fell or was knocked out of the arena: it's out
				} else {
					n++;
				}
			}
		}
		return n;
	}

	public int wave() {
		return wave;
	}

	/** Action bar during a wave: which wave, how many enemies are left. */
	private void status(ServerPlayer player, int alive) {
		String w = mode == Mode.INFINITE ? "Wave " + (wave + 1) : "Wave " + (wave + 1) + "/" + Waves.ALL.size();
		player.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal(w + "  ·  " + alive + " left").withStyle(ChatFormatting.GRAY)));
	}

	private void glowStragglers() {
		for (UUID id : enemies) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive() && !e.hasGlowingTag()) {
				e.setGlowingTag(true);
			}
		}
	}

	/** Mode of the player's arena, or null (tests). */
	@Nullable
	public static Mode modeOf(ServerPlayer player) {
		Arena a = ACTIVE.get(player.getUUID());
		return a == null ? null : a.mode;
	}

	/** Test hook: the wave currently being fought or counted down to. */
	@Nullable
	public static Waves.Wave currentWaveOf(ServerPlayer player) {
		Arena a = ACTIVE.get(player.getUUID());
		return a == null ? null : a.current;
	}

	/** Centre of the player's arena floor, or null (tests). */
	@Nullable
	public static BlockPos centerOf(ServerPlayer player) {
		Arena a = ACTIVE.get(player.getUUID());
		return a == null ? null : a.center;
	}

	// ------------------------------------------------------------------ end

	private void finish(ServerPlayer player, boolean victory) {
		if (phase == Phase.ENDING) {
			return;
		}
		if (mode == Mode.INFINITE) {
			recordInfinite(player);
		}
		phase = Phase.ENDING;
		timer = END_TICKS;
		removeEnemies();
		int seconds = (int) ((level.getGameTime() - startTick) / 20);
		String time = ArenaRecords.time(seconds);
		String stats = knockouts + " knock-outs, best combo " + maxCombo + ", " + time;
		if (mode == Mode.INFINITE) {
			FreeflowMod.LOGGER.info("{}'s infinite challenge: cleared {} waves, {}", player.getName().getString(), wave, stats);
			title(player, Component.literal(wave + (wave == 1 ? " wave cleared" : " waves cleared")).withStyle(ChatFormatting.GOLD),
					Component.literal(newRecord ? "New record!" : stats));
			player.sendSystemMessage(Component.literal("Infinite challenge: " + wave + " waves cleared (" + stats + ")"
					+ (newRecord ? ". New record!" : "")).withStyle(ChatFormatting.GOLD));
			if (newRecord) {
				level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			}
			return;
		}
		FreeflowMod.LOGGER.info("{}'s combat challenge: {} at wave {} after {}", player.getName().getString(),
				victory ? "won" : "defeated", Math.min(wave + 1, Waves.ALL.size()), time);
		if (victory) {
			newRecord = ArenaRecords.get(level.getServer()).submitClassic(player.getUUID(), player.getName().getString(), seconds);
			title(player, Component.literal("Challenge complete").withStyle(ChatFormatting.GOLD),
					Component.literal(newRecord ? "New best time: " + time : "All " + Waves.ALL.size() + " waves in " + time));
			player.sendSystemMessage(Component.literal("Combat challenge complete (" + stats + ")"
					+ (newRecord ? ". New best time!" : "")).withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
		} else {
			title(player, Component.literal("Defeated").withStyle(ChatFormatting.RED),
					Component.literal("Reached wave " + (wave + 1) + " of " + Waves.ALL.size() + " (" + time + ")"));
		}
	}

	/** Saves the infinite run's result: the waves fully cleared. */
	private void recordInfinite(ServerPlayer player) {
		newRecord = ArenaRecords.get(level.getServer()).submitInfinite(player.getUUID(), player.getName().getString(), wave, knockouts);
	}

	/** Sends the player back and removes everything the challenge put in the world. */
	private void close(@Nullable ServerPlayer player) {
		ACTIVE.remove(owner);
		removeEnemies();
		sweepDrops();
		if (player != null && player.isAlive()) {
			ServerLevel back = level.getServer().getLevel(returnDimension);
			if (back != null) {
				player.teleportTo(back, returnPos.x, returnPos.y, returnPos.z, returnYaw, returnPitch);
				player.fallDistance = 0;
			}
		}
		demolish();
	}

	private void removeEnemies() {
		for (UUID id : enemies) {
			Entity e = level.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		enemies.clear();
		toSpawn.clear();
	}

	/** Dropped items, arrows and XP orbs left on the arena floor. */
	private void sweepDrops() {
		AABB box = new AABB(center).inflate(RADIUS + 3, HEADROOM, RADIUS + 3);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box,
				e -> e instanceof ItemEntity || e instanceof net.minecraft.world.entity.projectile.AbstractArrow)) {
			e.discard();
		}
		// XP orbs fly to the player; leftovers are swept with the arena.
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
			if (phase == Phase.ENDING) {
				orb.discard();
			}
		}
	}

	private static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}
}

package com.borislav.freeflow.arena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Best results per player, saved with the world (data/freeflow_arena.dat): the fastest
 * classic win and the most waves cleared in infinite mode.
 */
public final class ArenaRecords extends SavedData {

	public static final class Entry {
		public String name = "";
		/** Fastest classic win in seconds; 0 = never won. */
		public int classicSeconds;
		public int infiniteWaves;
		/** Knock-outs in the best infinite run. */
		public int infiniteKos;
	}

	private final Map<UUID, Entry> entries = new HashMap<>();

	public static ArenaRecords get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(ArenaRecords::new, ArenaRecords::load, null), "freeflow_arena");
	}

	public Entry of(UUID id) {
		return entries.computeIfAbsent(id, k -> new Entry());
	}

	/** Returns true if this is a new best classic time. */
	public boolean submitClassic(UUID id, String name, int seconds) {
		Entry e = of(id);
		e.name = name;
		boolean best = e.classicSeconds == 0 || seconds < e.classicSeconds;
		if (best) {
			e.classicSeconds = seconds;
		}
		setDirty();
		return best;
	}

	/** Returns true if this is a new most-waves record. */
	public boolean submitInfinite(UUID id, String name, int waves, int kos) {
		Entry e = of(id);
		e.name = name;
		boolean best = waves > e.infiniteWaves || (waves == e.infiniteWaves && waves > 0 && kos > e.infiniteKos);
		if (best) {
			e.infiniteWaves = waves;
			e.infiniteKos = kos;
		}
		setDirty();
		return best;
	}

	/** The best infinite runs on this world, most waves first. */
	public List<Entry> topInfinite(int n) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values()) {
			if (e.infiniteWaves > 0) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingInt((Entry e) -> -e.infiniteWaves).thenComparingInt(e -> -e.infiniteKos));
		return out.subList(0, Math.min(n, out.size()));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag players = new CompoundTag();
		entries.forEach((id, e) -> {
			CompoundTag t = new CompoundTag();
			t.putString("name", e.name);
			t.putInt("classicSeconds", e.classicSeconds);
			t.putInt("infiniteWaves", e.infiniteWaves);
			t.putInt("infiniteKos", e.infiniteKos);
			players.put(id.toString(), t);
		});
		tag.put("players", players);
		return tag;
	}

	private static ArenaRecords load(CompoundTag tag, HolderLookup.Provider registries) {
		ArenaRecords r = new ArenaRecords();
		CompoundTag players = tag.getCompound("players");
		for (String key : players.getAllKeys()) {
			try {
				CompoundTag t = players.getCompound(key);
				Entry e = new Entry();
				e.name = t.getString("name");
				e.classicSeconds = t.getInt("classicSeconds");
				e.infiniteWaves = t.getInt("infiniteWaves");
				e.infiniteKos = t.getInt("infiniteKos");
				r.entries.put(UUID.fromString(key), e);
			} catch (IllegalArgumentException ignored) {
				// not a UUID: skip
			}
		}
		return r;
	}

	public static String time(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}

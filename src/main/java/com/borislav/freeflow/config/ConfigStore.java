package com.borislav.freeflow.config;

import com.borislav.freeflow.FreeflowMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and saves the {@link Setting} fields of {@link FreeflowConfig} as {@code config/freeflow.json}.
 * <p>
 * Two sets of values exist: the player's own ({@link #local}, what the file and settings screen hold)
 * and the live ones in the {@link FreeflowConfig} fields that the game reads. They are the same
 * except on a client playing on someone else's server, where the server's gameplay values are live
 * ({@link #applyRemote}) and the player's own come back on disconnect ({@link #applyLocal}).
 */
public final class ConfigStore {
	private ConfigStore() {}

	/** One setting: its field, its annotation and its default value. */
	public record Entry(Field field, Setting setting, Object defaultValue) {
		public String key() {
			return field.getName();
		}

		public Class<?> type() {
			return field.getType();
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Entry> ENTRIES = scan();
	private static final Map<String, Object> local = new LinkedHashMap<>();
	private static Path file = FabricLoader.getInstance().getConfigDir().resolve("freeflow.json");

	static {
		for (Entry e : ENTRIES) {
			local.put(e.key(), copy(e.defaultValue()));
		}
	}

	private static List<Entry> scan() {
		List<Entry> out = new ArrayList<>();
		for (Field f : FreeflowConfig.class.getDeclaredFields()) {
			Setting s = f.getAnnotation(Setting.class);
			if (s == null || !Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			try {
				out.add(new Entry(f, s, copy(f.get(null))));
			} catch (IllegalAccessException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return Collections.unmodifiableList(out);
	}

	public static List<Entry> entries() {
		return ENTRIES;
	}

	/** Test hook: read and write another file. */
	public static void useFile(Path path) {
		file = path;
	}

	// ------------------------------------------------------------------ the player's own values

	public static Object getLocal(Entry e) {
		return local.get(e.key());
	}

	public static void setLocal(Entry e, Object value) {
		local.put(e.key(), clamp(e, value));
	}

	/** Reads the file into the player's own values and makes them live; writes it if missing or outdated. */
	public static void load() {
		boolean rewrite = true;
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
				read(root, local, false);
				rewrite = !root.keySet().containsAll(local.keySet());
			} catch (Exception ex) {
				FreeflowMod.LOGGER.error("Could not read {}; using defaults", file, ex);
				rewrite = false; // keep the broken file for the player to fix
			}
		}
		applyLocal();
		if (rewrite) {
			save();
		}
	}

	/** Writes the player's own values to the file. */
	public static void save() {
		JsonObject root = write(local, false);
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(root, w);
			}
		} catch (IOException ex) {
			FreeflowMod.LOGGER.error("Could not write {}", file, ex);
		}
	}

	/** Makes the player's own values live (all of them). */
	public static void applyLocal() {
		for (Entry e : ENTRIES) {
			set(e, local.get(e.key()));
		}
	}

	/** Makes only the player's own client-side values live (camera, effects): gameplay stays as the server set it. */
	public static void applyLocalClientOnly() {
		for (Entry e : ENTRIES) {
			if (e.setting().client()) {
				set(e, local.get(e.key()));
			}
		}
	}

	// ------------------------------------------------------------------ server sync

	/** The live gameplay values, as sent to players joining this server. */
	public static String gameplayJson() {
		Map<String, Object> live = new LinkedHashMap<>();
		for (Entry e : ENTRIES) {
			if (!e.setting().client()) {
				live.put(e.key(), get(e));
			}
		}
		return write(live, true).toString();
	}

	/** Makes a server's gameplay values live on this client. Client-side settings are never touched. */
	public static void applyRemote(String json) {
		Map<String, Object> values = new LinkedHashMap<>();
		read(JsonParser.parseString(json).getAsJsonObject(), values, true);
		for (Entry e : ENTRIES) {
			if (!e.setting().client() && values.containsKey(e.key())) {
				set(e, values.get(e.key()));
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void read(JsonObject root, Map<String, Object> into, boolean gameplayOnly) {
		for (Entry e : ENTRIES) {
			if (gameplayOnly && e.setting().client()) {
				continue;
			}
			JsonElement j = root.get(e.key());
			if (j == null || j.isJsonNull()) {
				continue;
			}
			try {
				into.put(e.key(), clamp(e, fromJson(e.type(), j)));
			} catch (RuntimeException ex) {
				FreeflowMod.LOGGER.warn("Ignoring bad value for {}: {}", e.key(), j);
			}
		}
	}

	private static JsonObject write(Map<String, Object> values, boolean gameplayOnly) {
		JsonObject root = new JsonObject();
		for (Entry e : ENTRIES) {
			if ((gameplayOnly && e.setting().client()) || !values.containsKey(e.key())) {
				continue;
			}
			root.add(e.key(), toJson(values.get(e.key())));
		}
		return root;
	}

	private static Object fromJson(Class<?> type, JsonElement j) {
		if (type == boolean.class) {
			return j.getAsBoolean();
		} else if (type == int.class) {
			return j.getAsInt();
		} else if (type == long.class) {
			return j.getAsLong();
		} else if (type == float.class) {
			return j.getAsFloat();
		} else if (type == double.class) {
			return j.getAsDouble();
		} else if (type == List.class) {
			List<String> list = new ArrayList<>();
			for (JsonElement s : j.getAsJsonArray()) {
				list.add(s.getAsString());
			}
			return list;
		}
		throw new IllegalArgumentException("unsupported setting type " + type);
	}

	private static JsonElement toJson(Object v) {
		if (v instanceof Boolean b) {
			return new JsonPrimitive(b);
		} else if (v instanceof Number n) {
			return new JsonPrimitive(n);
		} else if (v instanceof List<?> list) {
			JsonArray a = new JsonArray();
			for (Object s : list) {
				a.add(String.valueOf(s));
			}
			return a;
		}
		throw new IllegalArgumentException("unsupported setting value " + v);
	}

	/** Keeps numbers within the setting's limits and trims list entries. */
	public static Object clamp(Entry e, Object v) {
		double min = e.setting().min();
		double max = e.setting().max();
		if (v instanceof Integer i) {
			return (int) Math.max(min, Math.min(max, i));
		} else if (v instanceof Long l) {
			return (long) Math.max(min, Math.min(max, l));
		} else if (v instanceof Float f) {
			return (float) Math.max(min, Math.min(max, f));
		} else if (v instanceof Double d) {
			return Math.max(min, Math.min(max, d));
		} else if (v instanceof List<?> list) {
			List<String> out = new ArrayList<>();
			for (Object s : list) {
				String t = String.valueOf(s).trim();
				if (!t.isEmpty()) {
					out.add(t);
				}
			}
			return out;
		}
		return v;
	}

	private static Object copy(Object v) {
		return v instanceof List<?> list ? new ArrayList<>(list) : v;
	}

	private static Object get(Entry e) {
		try {
			return copy(e.field().get(null));
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void set(Entry e, Object v) {
		try {
			e.field().set(null, copy(v));
		} catch (IllegalAccessException | IllegalArgumentException ex) {
			FreeflowMod.LOGGER.warn("Could not apply setting {} = {}", e.key(), v, ex);
		}
	}
}

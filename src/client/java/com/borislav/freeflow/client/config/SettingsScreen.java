package com.borislav.freeflow.client.config;

import com.borislav.freeflow.config.ConfigStore;
import com.borislav.freeflow.config.ConfigSync;
import com.borislav.freeflow.config.Setting;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The settings screen (Mods menu, or the settings key in game), built from the {@link Setting}
 * annotations: one tab per category, one row per setting, each with its default and limits.
 * <p>
 * It edits the player's own values. They take effect at once in your own worlds; on someone else's
 * server the server's gameplay values stay in force until you leave, and only camera and effect
 * settings change straight away.
 */
public final class SettingsScreen {
	private SettingsScreen() {}

	public static Screen create(Screen parent) {
		return create(parent, Setting.Category.GENERAL);
	}

	/** Opens on the given tab. */
	public static Screen create(Screen parent, Setting.Category openTab) {
		Minecraft mc = Minecraft.getInstance();
		boolean onRemoteServer = mc.getConnection() != null && !mc.hasSingleplayerServer();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Freeflow Combat Settings"));
		ConfigEntryBuilder eb = builder.entryBuilder();

		Map<Setting.Category, ConfigCategory> tabs = new EnumMap<>(Setting.Category.class);
		for (Setting.Category c : Setting.Category.values()) {
			ConfigCategory tab = builder.getOrCreateCategory(Component.literal(c.title));
			tabs.put(c, tab);
			if (onRemoteServer && c != Setting.Category.CAMERA && c != Setting.Category.EFFECTS
					&& c != Setting.Category.CONTROLLER) {
				tab.addEntry(eb.startTextDescription(Component.literal(
						"You're on a server: its gameplay settings apply while you play there. "
								+ "Changes here are saved for your own worlds.").withStyle(ChatFormatting.YELLOW)).build());
			}
		}

		for (ConfigStore.Entry e : ConfigStore.entries()) {
			AbstractConfigListEntry<?> row = row(eb, e);
			if (row != null) {
				tabs.get(e.setting().category()).addEntry(row);
			}
		}

		builder.setFallbackCategory(tabs.get(openTab));
		builder.setSavingRunnable(() -> {
			ConfigStore.save();
			if (onRemoteServer) {
				ConfigStore.applyLocalClientOnly();
			} else {
				ConfigStore.applyLocal();
				IntegratedServer server = mc.getSingleplayerServer();
				if (server != null) {
					server.execute(() -> ConfigSync.sendAll(server)); // LAN guests get the new values too
				}
			}
		});
		return builder.build();
	}

	@SuppressWarnings("unchecked")
	private static AbstractConfigListEntry<?> row(ConfigEntryBuilder eb, ConfigStore.Entry e) {
		Setting s = e.setting();
		Component name = Component.literal(s.name());
		Component[] tip = s.tip().isEmpty() ? new Component[0] : new Component[] {Component.literal(s.tip())};
		Object value = ConfigStore.getLocal(e);
		Object def = e.defaultValue();
		Class<?> type = e.type();

		if (type == boolean.class) {
			return eb.startBooleanToggle(name, (Boolean) value).setDefaultValue((Boolean) def).setTooltip(tip)
					.setSaveConsumer(v -> ConfigStore.setLocal(e, v)).build();
		} else if (type == int.class) {
			return eb.startIntField(name, (Integer) value).setDefaultValue((Integer) def)
					.setMin((int) s.min()).setMax((int) s.max()).setTooltip(tip)
					.setSaveConsumer(v -> ConfigStore.setLocal(e, v)).build();
		} else if (type == long.class) {
			return eb.startLongField(name, (Long) value).setDefaultValue((Long) def)
					.setMin((long) s.min()).setMax((long) s.max()).setTooltip(tip)
					.setSaveConsumer(v -> ConfigStore.setLocal(e, v)).build();
		} else if (type == float.class) {
			return eb.startFloatField(name, (Float) value).setDefaultValue((Float) def)
					.setMin((float) s.min()).setMax((float) s.max()).setTooltip(tip)
					.setSaveConsumer(v -> ConfigStore.setLocal(e, v)).build();
		} else if (type == double.class) {
			return eb.startDoubleField(name, (Double) value).setDefaultValue((Double) def)
					.setMin(s.min()).setMax(s.max()).setTooltip(tip)
					.setSaveConsumer(v -> ConfigStore.setLocal(e, v)).build();
		} else if (type == List.class) {
			return eb.startStrList(name, new ArrayList<>((List<String>) value))
					.setDefaultValue(new ArrayList<>((List<String>) def)).setTooltip(tip)
					.setExpanded(false).setSaveConsumer(v -> ConfigStore.setLocal(e, v)).build();
		}
		return null;
	}
}

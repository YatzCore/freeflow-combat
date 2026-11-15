package com.borislav.freeflow.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Freeflow key bindings (rebindable in Options > Controls).
 * Strike uses the vanilla attack key (left mouse), intercepted while freeflow is active.
 * Defaults avoid vanilla binds (F = swap hands, C/X = toolbars).
 */
public final class Keybinds {
	private Keybinds() {}

	private static final String CATEGORY = "key.categories.freeflow";

	public static KeyMapping counter;
	public static KeyMapping evade;
	public static KeyMapping stun;
	public static KeyMapping groundTakedown;
	public static KeyMapping specialTakedown;
	public static KeyMapping toggleMode;
	public static KeyMapping debugOverlay;
	public static KeyMapping swapShoulder;
	public static KeyMapping toggleCamera;

	public static void register() {
		counter = key("counter", GLFW.GLFW_KEY_R);
		evade = key("evade", GLFW.GLFW_KEY_LEFT_ALT);
		stun = key("stun", GLFW.GLFW_KEY_G);
		groundTakedown = key("ground_takedown", GLFW.GLFW_KEY_V);
		specialTakedown = key("special_takedown", GLFW.GLFW_KEY_Z);
		toggleMode = key("toggle_mode", GLFW.GLFW_KEY_B);
		debugOverlay = key("debug_overlay", GLFW.GLFW_KEY_F7);
		swapShoulder = key("swap_shoulder", GLFW.GLFW_KEY_H);
		toggleCamera = key("toggle_camera", GLFW.GLFW_KEY_J);
	}

	private static KeyMapping key(String name, int glfwKey) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.freeflow." + name, InputConstants.Type.KEYSYM, glfwKey, CATEGORY));
	}
}

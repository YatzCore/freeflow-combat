package com.borislav.freeflow.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link FreeflowConfig} field as a user setting: it is saved to {@code config/freeflow.json},
 * shown on the settings screen and, unless {@link #client()}, sent from the server to its players.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Setting {
	Category category();

	/** Label on the settings screen. */
	String name();

	/** Tooltip on the settings screen. */
	String tip() default "";

	double min() default 0;

	double max() default 1000;

	/**
	 * Only affects this player's own view and controls (camera, effects, HUD). Client settings are
	 * never overridden by a server; the rest are gameplay and follow the server you play on.
	 */
	boolean client() default false;

	enum Category {
		GENERAL("General"),
		STRIKES("Strikes & Combo"),
		ENEMIES("Enemies"),
		DEFENCE("Counter & Evade"),
		TAKEDOWNS("Stun & Takedowns"),
		CAMERA("Camera"),
		EFFECTS("Effects & HUD");

		public final String title;

		Category(String title) {
			this.title = title;
		}
	}
}

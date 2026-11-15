package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: something happened to an enemy, for client feedback (markers, HUD flashes, locks).
 * {@code ticks} is a duration where it applies (stun / knockdown / takedown length).
 */
public record CombatEventPayload(Type event, int mobId, int ticks) implements CustomPacketPayload {
	public enum Type {
		STUN, STUN_END, KNOCKDOWN, TAKEDOWN_START, TAKEDOWN, TAKEDOWN_INTERRUPTED, SPECIAL_TAKEDOWN;

		static Type byId(int id) {
			Type[] v = values();
			return id >= 0 && id < v.length ? v[id] : STUN_END;
		}
	}

	public static final CustomPacketPayload.Type<CombatEventPayload> TYPE =
			new CustomPacketPayload.Type<>(FreeflowMod.id("combat_event"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CombatEventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(Type::byId, Type::ordinal), CombatEventPayload::event,
			ByteBufCodecs.VAR_INT, CombatEventPayload::mobId,
			ByteBufCodecs.VAR_INT, CombatEventPayload::ticks,
			CombatEventPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

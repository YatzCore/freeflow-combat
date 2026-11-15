package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: combo changed. {@code ticksLeft} is how long until it times out,
 * {@code staggerTicks} how long the player is staggered (0 = not).
 */
public record ComboPayload(int combo, int ticksLeft, int staggerTicks, Event event) implements CustomPacketPayload {
	public enum Event {
		HIT, BREAK, STAGGER;

		static Event byId(int id) {
			Event[] v = values();
			return id >= 0 && id < v.length ? v[id] : BREAK;
		}
	}

	public static final Type<ComboPayload> TYPE = new Type<>(FreeflowMod.id("combo"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ComboPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ComboPayload::combo,
			ByteBufCodecs.VAR_INT, ComboPayload::ticksLeft,
			ByteBufCodecs.VAR_INT, ComboPayload::staggerTicks,
			ByteBufCodecs.VAR_INT.map(Event::byId, Event::ordinal), ComboPayload::event,
			ComboPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

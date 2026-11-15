package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: play combat animation {@code name} on player entity {@code entityId}. */
public record AnimationPayload(int entityId, String name) implements CustomPacketPayload {
	public static final Type<AnimationPayload> TYPE = new Type<>(FreeflowMod.id("animation"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AnimationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AnimationPayload::entityId,
			ByteBufCodecs.STRING_UTF8, AnimationPayload::name,
			AnimationPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

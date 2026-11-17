package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: play combat animation {@code name} on player entity {@code entityId}. {@code lead} is how
 * many ticks from now the move's blow lands (the client lines the clip's impact up with it), or
 * {@link #FROM_START} to simply play the clip from its beginning.
 */
public record AnimationPayload(int entityId, String name, int lead) implements CustomPacketPayload {
	public static final int FROM_START = -1;
	/** As {@code name}: stop the move that is playing instead of starting one. */
	public static final String STOP = "";

	public static final Type<AnimationPayload> TYPE = new Type<>(FreeflowMod.id("animation"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AnimationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AnimationPayload::entityId,
			ByteBufCodecs.STRING_UTF8, AnimationPayload::name,
			ByteBufCodecs.VAR_INT, AnimationPayload::lead,
			AnimationPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

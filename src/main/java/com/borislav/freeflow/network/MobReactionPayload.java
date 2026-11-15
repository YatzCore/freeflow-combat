package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.ai.MobReaction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C (to everyone tracking the mob): play an enemy reaction animation. */
public record MobReactionPayload(int mobId, MobReaction reaction, int ticks, int param, int attackerId)
		implements CustomPacketPayload {
	public static final Type<MobReactionPayload> TYPE = new Type<>(FreeflowMod.id("mob_reaction"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MobReactionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MobReactionPayload::mobId,
			ByteBufCodecs.VAR_INT.map(MobReaction::byId, MobReaction::ordinal), MobReactionPayload::reaction,
			ByteBufCodecs.VAR_INT, MobReactionPayload::ticks,
			ByteBufCodecs.VAR_INT, MobReactionPayload::param,
			ByteBufCodecs.VAR_INT, MobReactionPayload::attackerId,
			MobReactionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

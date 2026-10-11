package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.RealisticSurvival;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Packets for the fire-lighting game. */
public final class CampfireNet {

    /** Server to client: open the lighting game for the campfire at pos. */
    /** mode: 0 flint and steel / striker, 1 bow drill, 2 hand drill (rubbing game: hits = strokes needed, zone = heat lost per second). */
    public record OpenLighting(BlockPos pos, int hits, float zone, int mode, boolean wet) implements CustomPacketPayload {
        public static final Type<OpenLighting> TYPE = new Type<>(RealisticSurvival.id("open_lighting"));
        public static final StreamCodec<ByteBuf, OpenLighting> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenLighting::pos,
                ByteBufCodecs.VAR_INT, OpenLighting::hits,
                ByteBufCodecs.FLOAT, OpenLighting::zone,
                ByteBufCodecs.VAR_INT, OpenLighting::mode,
                ByteBufCodecs.BOOL, OpenLighting::wet,
                OpenLighting::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: how the lighting game went. */
    public record LightingResult(BlockPos pos, boolean success) implements CustomPacketPayload {
        public static final Type<LightingResult> TYPE = new Type<>(RealisticSurvival.id("lighting_result"));
        public static final StreamCodec<ByteBuf, LightingResult> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LightingResult::pos,
                ByteBufCodecs.BOOL, LightingResult::success,
                LightingResult::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(OpenLighting.TYPE, OpenLighting.CODEC);
        registrar.playToServer(LightingResult.TYPE, LightingResult.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                RealisticCampfireBlock.finishLighting(player, payload.pos(), payload.success());
            }
        });
    }

    private CampfireNet() {}
}

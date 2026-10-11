package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.RealisticSurvival;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Mining ore is a little game: hitting an ore block with a pickaxe that can mine it opens a 2D
 * view of the ore's own texture. You walk the pickaxe in from the edge, smash through the rock
 * and prise out each piece of ore. You get the share of the ore you got out cleanly.
 */
public final class OreMining {
    /** How long the dig lasts. */
    public static final int TIME_TICKS = 45 * 20;
    private static final long SESSION_TIMEOUT = TIME_TICKS + 400;

    private record Session(BlockPos pos, BlockState state, long started) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(BlockTags.ORES);
    }

    /** Whether hitting this block starts the mining game. Same answer on both sides. */
    public static boolean applies(Player player, BlockState state) {
        if (!Config.ORE_MINIGAME.getAsBoolean()) return false;
        if (player.isCreative() || player.isSpectator()) return false;
        return isOre(state) && player.hasCorrectToolForDrops(state);
    }

    /** What the rock around the ore is: 0 stone, 1 deepslate, 2 netherrack. */
    public static int rockKind(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("deepslate")) return 1;
        if (path.contains("nether") || path.contains("ancient_debris")) return 2;
        return 0;
    }

    /** How much rock one hard swing breaks, from the pickaxe's mining speed (efficiency counts). */
    public static int power(ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state) * ToolWear.factor(tool);
        return Math.max(1, Math.min(6, Math.round(speed / 2.0F)));
    }

    // ---- starting -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Player player = event.getEntity();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!applies(player, state)) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer sp) start(sp, event.getPos(), state);
    }

    public static void start(ServerPlayer player, BlockPos pos, BlockState state) {
        long now = player.level().getGameTime();
        Session old = SESSIONS.get(player.getUUID());
        if (old != null && now - old.started() < SESSION_TIMEOUT) return;
        SESSIONS.put(player.getUUID(), new Session(pos.immutable(), state, now));
        int rock = rockKind(state);
        PacketDistributor.sendToPlayer(player, new OpenMining(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                power(player.getMainHandItem(), state), rock, TIME_TICKS));
    }

    // ---- finishing -----------------------------------------------------------------------------

    /**
     * The dig is over. {@code collected} of {@code total} ore cells came out cleanly after
     * {@code actions} swings and taps. Returns how many items dropped (for the tests).
     */
    public static int finish(ServerPlayer player, BlockPos pos, int collected, int total, int actions) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null || !s.pos().equals(pos)) return -1;
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(pos);
        if (state != s.state() || !player.isWithinBlockInteractionRange(pos, 2.0)) return -1;
        if (actions <= 0) return 0; // walked away without touching it
        ItemStack tool = player.getMainHandItem();
        if (!player.hasCorrectToolForDrops(state)) return -1;

        total = Math.max(1, total);
        float share = Math.max(0.0F, Math.min(1.0F, collected / (float) total));
        BlockEntity be = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, player, tool);
        int xp = state.getExpDrop(level, pos, be, player, tool);
        int dropped = 0;
        for (ItemStack d : drops) {
            int count = scaled(d.getCount(), share, player.getRandom().nextFloat());
            if (count <= 0) continue;
            Block.popResource(level, pos, d.copyWithCount(count));
            dropped += count;
        }
        int x = scaled(xp, share, player.getRandom().nextFloat());
        if (x > 0) state.getBlock().popExperience(level, pos, x);

        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        tool.hurtAndBreak(1 + actions / 12, player, EquipmentSlot.MAINHAND);
        ToolWear.wear(player, tool, 1 + actions / 8);
        Stamina.spend(player, 0.3F + actions * 0.08F);
        if (Config.WORK_TIRES.getAsBoolean()) player.causeFoodExhaustion(0.02F * actions);
        return dropped;
    }

    /** count * share, with the leftover fraction as a chance of one more. */
    public static int scaled(int count, float share, float roll) {
        if (share >= 1.0F) return count;
        float n = count * share;
        int whole = (int) Math.floor(n);
        return whole + (roll < n - whole ? 1 : 0);
    }

    public static void forget(Player player) {
        SESSIONS.remove(player.getUUID());
    }

    // ---- network --------------------------------------------------------------------------------

    /** Server to client: open the mining game. */
    public record OpenMining(BlockPos pos, String block, int power, int rock, int timeTicks) implements CustomPacketPayload {
        public static final Type<OpenMining> TYPE = new Type<>(RealisticSurvival.id("open_mining"));
        public static final StreamCodec<ByteBuf, OpenMining> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenMining::pos,
                ByteBufCodecs.STRING_UTF8, OpenMining::block,
                ByteBufCodecs.VAR_INT, OpenMining::power,
                ByteBufCodecs.VAR_INT, OpenMining::rock,
                ByteBufCodecs.VAR_INT, OpenMining::timeTicks,
                OpenMining::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: how the dig went. */
    public record MiningResult(BlockPos pos, int collected, int total, int actions) implements CustomPacketPayload {
        public static final Type<MiningResult> TYPE = new Type<>(RealisticSurvival.id("mining_result"));
        public static final StreamCodec<ByteBuf, MiningResult> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MiningResult::pos,
                ByteBufCodecs.VAR_INT, MiningResult::collected,
                ByteBufCodecs.VAR_INT, MiningResult::total,
                ByteBufCodecs.VAR_INT, MiningResult::actions,
                MiningResult::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(OpenMining.TYPE, OpenMining.CODEC);
        registrar.playToServer(MiningResult.TYPE, MiningResult.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) finish(player, p.pos(), p.collected(), p.total(), p.actions());
        });
    }

    private OreMining() {}
}

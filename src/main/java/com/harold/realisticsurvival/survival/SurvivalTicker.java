package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Thirst drain and body temperature, run on the server for every player. */
public final class SurvivalTicker {
    /** Thirst lost per tick at rest with thirstRate 1: a full bar lasts ~16 minutes. */
    private static final float BASE_DRAIN = SurvivalState.MAX_THIRST / (16 * 60 * 20);

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isAlive()) return;
        SurvivalState s = SurvivalState.get(player);
        ServerLevel level = (ServerLevel) player.level();
        boolean exempt = player.isCreative() || player.isSpectator();

        if (Config.THIRST.getAsBoolean() && !exempt) tickThirst(player, level, s);
        if (Config.TEMPERATURE.getAsBoolean() && !exempt) {
            if (player.tickCount % 20 == 0) tickTemperature(player, level, s);
            // frost on the screen while cold (vanilla's freezing overlay)
            if (s.bodyTemp < 35.5F && !player.isInPowderSnow) {
                player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() - 1, (int) ((35.5F - s.bodyTemp) * 70)));
            }
        } else if (exempt) {
            s.bodyTemp = SurvivalState.NORMAL_TEMP;
        }
        if (Config.STAMINA.getAsBoolean() && !exempt) Stamina.tick(player, s);
        else if (exempt) { s.stamina = SurvivalState.MAX_STAMINA; s.winded = false; }
        if (s.warnCooldown > 0) s.warnCooldown--;

        if (player.tickCount % 10 == 0) syncHud(player, s);
    }

    private static void tickThirst(ServerPlayer player, ServerLevel level, SurvivalState s) {
        float drain = BASE_DRAIN * (float) Config.THIRST_RATE.getAsDouble();
        if (player.isSprinting()) drain *= 2.5F;
        else if (player.isSwimming()) drain *= 1.5F;
        if (s.bodyTemp > 37.4F) drain *= 1.0F + (s.bodyTemp - 37.4F) * 1.2F; // sweating
        if (level.dimension() == Level.NETHER) drain *= 1.5F;
        s.addThirst(-drain);

        if (s.thirst <= 0.0F && player.tickCount % 80 == 0) {
            player.hurtServer(level, ModDamage.source(level, ModDamage.DEHYDRATION), 1.0F);
        }
        if (s.thirst <= 6.0F && player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false, true));
            warn(player, s, "message.realisticsurvival.thirsty");
        }
    }

    // ---- temperature -------------------------------------------------------------------------

    private static void tickTemperature(ServerPlayer player, ServerLevel level, SurvivalState s) {
        float air = ambient(player, level);
        s.ambient = air;

        float insulation = insulation(player);
        float comfortLow = 12.0F - insulation * 2.2F;
        float comfortHigh = 32.0F - insulation * 1.0F;
        if (s.thirst < 4.0F) comfortHigh -= 4.0F; // can't sweat

        float target = SurvivalState.NORMAL_TEMP;
        if (air < comfortLow) target += (air - comfortLow) * 0.18F;
        else if (air > comfortHigh) target += (air - comfortHigh) * 0.18F;
        if (player.isOnFire()) target = Math.max(target, 41.5F);
        target = Mth.clamp(target, 28.0F, 43.0F);

        float rate = player.isInWater() ? 0.06F : 0.02F;
        s.bodyTemp += (target - s.bodyTemp) * rate;

        applyTemperatureEffects(player, level, s);
    }

    private static void applyTemperatureEffects(ServerPlayer player, ServerLevel level, SurvivalState s) {
        float t = s.bodyTemp;
        if (t < 35.5F) {
            int amp = t < 34.0F ? 1 : 0;
            player.addEffect(new MobEffectInstance(ModRegistry.HYPOTHERMIA, 60, amp, false, false, true));
            warn(player, s, "message.realisticsurvival.cold");
            if (t < 33.0F && player.tickCount % 60 == 0) {
                player.hurtServer(level, ModDamage.source(level, ModDamage.HYPOTHERMIA), 1.0F);
            }
        } else if (t > 38.5F) {
            int amp = t > 39.5F ? 1 : 0;
            player.addEffect(new MobEffectInstance(ModRegistry.HEATSTROKE, 60, amp, false, false, true));
            warn(player, s, "message.realisticsurvival.hot");
            if (t > 39.5F && player.tickCount % 200 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false, true));
            }
            if (t > 40.5F && player.tickCount % 60 == 0) {
                player.hurtServer(level, ModDamage.source(level, ModDamage.HEATSTROKE), 1.0F);
            }
        }
    }

    /** "Feels like" air temperature in degrees C at the player. */
    public static float ambient(Player player, ServerLevel level) {
        BlockPos pos = player.blockPosition();
        if (level.dimension() == Level.NETHER) return 44.0F + heatSources(level, pos);
        if (level.dimension() == Level.END) return 6.0F + heatSources(level, pos);

        Holder<Biome> biome = level.getBiome(pos);
        float base = biome.value().getBaseTemperature();
        boolean dry = base > 1.5F && !biome.value().hasPrecipitation();
        float air = base * 20.0F - 2.0F;

        boolean sky = level.canSeeSky(pos.above());
        // day/night swing: +1 at noon, -1 at midnight
        long time = level.getOverworldClockTime() % 24000L;
        float sun = Mth.cos((float) ((time - 6000L) / 24000.0 * Math.PI * 2.0));
        float swing = dry ? 10.0F : 5.0F;
        air += sun * swing;
        if (!sky && sun > 0) air -= (dry ? 6.0F : 2.0F) * sun; // shade

        // altitude: thinner, colder air
        if (pos.getY() > 90) air -= (pos.getY() - 90) * 0.08F;

        // underground the air settles at cave temperature
        int depth = level.getSeaLevel() - 8 - pos.getY();
        if (!sky && depth > 0) {
            float f = Math.min(1.0F, depth / 24.0F);
            air = air + (14.0F - air) * f;
            if (pos.getY() < 0) air += -pos.getY() * 0.12F; // deep rock is warm
        }

        if (sky && level.isRainingAt(pos.above())) air -= base < 0.15F ? 8.0F : 5.0F; // wet clothes
        if (player.isInWater()) air = air < 26.0F ? air - 10.0F : air - 4.0F;
        if (player.isInPowderSnow) air -= 20.0F;

        return air + heatSources(level, pos);
    }

    /** Warmth from nearby fires, lava and furnaces. */
    private static float heatSources(ServerLevel level, BlockPos center) {
        float heat = 0.0F;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-7, -2, -7), center.offset(7, 2, 7))) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            float strength = 0.0F;
            if (state.getBlock() instanceof com.harold.realisticsurvival.block.RealisticCampfireBlock) {
                // bigger fires are hotter and reach further; embers still give a little warmth
                int size = state.getValue(com.harold.realisticsurvival.block.RealisticCampfireBlock.SIZE);
                double d = Math.sqrt(p.distSqr(center));
                if (state.getValue(BlockStateProperties.LIT)) {
                    float reach = size == 1 ? 3.5F : size == 2 ? 5.0F : 7.5F;
                    if (d <= reach) strength = size == 1 ? 9.0F : size == 2 ? 14.0F : 22.0F;
                } else if (state.getValue(com.harold.realisticsurvival.block.RealisticCampfireBlock.EMBERS) && d <= 2.5) {
                    strength = 5.0F;
                }
            } else if (Math.abs(p.getX() - center.getX()) > 4 || Math.abs(p.getZ() - center.getZ()) > 4) {
                continue;
            } else if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) strength = 14.0F;
            else if (state.is(BlockTags.FIRE)) strength = 10.0F;
            else if (state.getFluidState().is(FluidTags.LAVA)) strength = 16.0F;
            else if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(BlockStateProperties.LIT)) strength = 7.0F;
            else if (state.is(Blocks.MAGMA_BLOCK)) strength = 4.0F;
            if (strength > 0.0F) {
                double d = Math.sqrt(p.distSqr(center));
                heat += strength / (1.0F + (float) d * 0.8F);
            }
        }
        return Math.min(heat, 35.0F);
    }

    /** How warm the player's clothes are. Fur ~2.5 per piece, leather 1.5, other armour 0.5. */
    public static float insulation(Player player) {
        float total = 0.0F;
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(ModRegistry.WARM_CLOTHING)) total += 2.5F;
            else if (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS)) total += 1.5F;
            else total += 0.5F;
        }
        return total;
    }

    private static void warn(ServerPlayer player, SurvivalState s, String key) {
        if (s.warnCooldown > 0) return;
        s.warnCooldown = 20 * 30;
        player.sendOverlayMessage(Component.translatable(key));
    }

    private static void syncHud(ServerPlayer player, SurvivalState s) {
        int thirst = Mth.ceil(s.thirst);
        if (player.getData(ModRegistry.THIRST_HUD) != thirst) player.setData(ModRegistry.THIRST_HUD, thirst);
        int temp = Math.round(s.bodyTemp * 10.0F);
        if (player.getData(ModRegistry.TEMP_HUD) != temp) player.setData(ModRegistry.TEMP_HUD, temp);
        int stamina = Math.round(s.stamina * 10.0F) + (s.winded ? 1000 : 0);
        if (player.getData(ModRegistry.STAMINA_HUD) != stamina) player.setData(ModRegistry.STAMINA_HUD, stamina);
    }

    /** Very thirsty bodies don't heal. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Config.THIRST.getAsBoolean()
                && !player.isCreative() && SurvivalState.get(player).thirst < 4.0F) {
            event.setAmount(event.getAmount() * 0.25F);
        }
    }

    private SurvivalTicker() {}
}

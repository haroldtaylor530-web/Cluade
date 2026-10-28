package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.List;

import com.harold.fieldkit.Fieldkit;
import com.harold.fieldkit.map.Gen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** -Dfieldkit.clienttest=true: makes a world, sets up a fight, and screenshots the HUD and every tab. */
public final class ClientTest {
    private static int ticks, worldTicks;
    private static boolean worldMade;
    private static final List<String> problems = new ArrayList<>();

    private ClientTest() {
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> Fieldkit.LOG.info("CLIENTTEST screenshot {}", name));
    }

    private static void server(java.util.function.Consumer<ServerPlayer> r) {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            r.accept(p);
        });
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (!worldMade && mc.screen instanceof TitleScreen && ticks > 60) {
            worldMade = true;
            Fieldkit.platform.createTestWorld("fieldkit_test", 12345L);
            return;
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null && worldTicks == 0) return;
        int t = ++worldTicks;
        try {
            step(mc, t);
        } catch (Throwable e) {
            problems.add(t + ": " + e);
            Fieldkit.LOG.error("CLIENTTEST step {} failed", t, e);
        }
    }

    private static void step(Minecraft mc, int t) {
        switch (t) {
            case 60 -> server(p -> {
                var level = p.serverLevel();
                level.setDayTime(18000);
                p.setItemSlot(EquipmentSlot.HEAD, damaged(Items.DIAMOND_HELMET, 0.3F));
                p.setItemSlot(EquipmentSlot.CHEST, damaged(Items.IRON_CHESTPLATE, 0.7F));
                p.setItemSlot(EquipmentSlot.LEGS, damaged(Items.DIAMOND_LEGGINGS, 0.1F));
                p.setItemSlot(EquipmentSlot.FEET, damaged(Items.IRON_BOOTS, 0.85F));
                p.setItemSlot(EquipmentSlot.MAINHAND, damaged(Items.DIAMOND_SWORD, 0.2F));
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                p.getInventory().add(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
                Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
                spawn(level, EntityType.ZOMBIE, p.position().add(look.scale(9)));
                spawn(level, EntityType.SKELETON, p.position().add(look.scale(13)).add(4, 0, 0));
                spawn(level, EntityType.CREEPER, p.position().add(-6, 0, -5));
                spawn(level, EntityType.COW, p.position().add(5, 0, 6));
                spawn(level, EntityType.VILLAGER, p.position().add(-4, 0, 7));
                p.setHealth(15);
            });
            case 130 -> shot("A_hud");
            case 132 -> {
                // hit the zombie: target info and the fight log
                Zombie z = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(30)).stream().findFirst().orElse(null);
                if (z != null) {
                    mc.gameMode.attack(mc.player, z);
                    mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                } else problems.add("no zombie to hit");
            }
            case 150 -> shot("B_target");
            case 152 -> {
                server(p -> p.sendSystemMessage(Component.literal("<Alex> hey " + p.getGameProfile().getName() + ", over here")));
                Fieldkit.LOG.error("Test error from the Fieldkit client test (this is expected)");
            }
            case 200 -> shot("C_notifications");
            case 205 -> {
                FieldkitScreen.openTab(0);
                mc.setScreen(new FieldkitScreen());
            }
            case 330 -> shot("D_map");
            case 335 -> MapTab.bpp = 16;
            case 470 -> shot("E_map_zoomed_out");
            case 475 -> {
                Gen g = Gen.current();
                if (g == null) {
                    problems.add("no generator in singleplayer");
                    return;
                }
                SeedTab.testSearch(g, biome("minecraft:cherry_grove"), 900, ResourceKey.create(Registries.STRUCTURE,
                        ResourceLocation.tryParse("minecraft:village_plains")), 600);
                if (mc.screen instanceof FieldkitScreen s) s.switchTo(1);
            }
            case 1100 -> shot("F_seed_finder");
            case 1105 -> {
                if (SeedTab.search != null && !SeedTab.search.results.isEmpty()) {
                    MapTab.preview(SeedTab.search.results.get(0).seed());
                    MapTab.bpp = 8;
                    if (mc.screen instanceof FieldkitScreen s) s.switchTo(0);
                } else problems.add("seed search found nothing (" + (SeedTab.search == null ? "not started"
                        : SeedTab.search.checked.get() + " checked, error " + SeedTab.search.error) + ")");
            }
            case 1230 -> shot("G_seed_on_map");
            case 1235 -> tab(mc, 2);
            case 1265 -> shot("H_console");
            case 1270 -> tab(mc, 3);
            case 1290 -> shot("I_combat");
            case 1295 -> tab(mc, 4);
            case 1315 -> shot("J_info");
            case 1320 -> tab(mc, 5);
            case 1340 -> shot("K_settings");
            case 1345 -> {
                MapTab.previewSeed = null;
                mc.setScreen(null);
            }
            case 1370 -> shot("L_hud_again");
            case 1380 -> {
                Fieldkit.LOG.info("CLIENTTEST seeds checked {} found {}", SeedTab.search == null ? 0 : SeedTab.search.checked.get(),
                        SeedTab.search == null ? 0 : SeedTab.search.results.size());
                Fieldkit.LOG.info(problems.isEmpty() ? "CLIENTTEST DONE ok" : "CLIENTTEST DONE problems: " + problems);
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void tab(Minecraft mc, int i) {
        if (mc.screen instanceof FieldkitScreen s) s.switchTo(i);
    }

    private static ResourceKey<net.minecraft.world.level.biome.Biome> biome(String id) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.tryParse(id));
    }

    private static ItemStack damaged(net.minecraft.world.item.Item item, float worn) {
        ItemStack s = new ItemStack(item);
        if (s.isDamageableItem()) s.setDamageValue((int) (s.getMaxDamage() * worn));
        return s;
    }

    private static void spawn(net.minecraft.server.level.ServerLevel level, EntityType<? extends Mob> type, Vec3 at) {
        Mob m = type.create(level);
        if (m == null) return;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) at.x, (int) at.z);
        m.moveTo(at.x, y, at.z, 0, 0);
        level.addFreshEntity(m);
    }
}

package com.harold.corpsedecay.client;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.harold.corpsedecay.corpse.CorpseEntity;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * CI only (-Dcorpsedecay.clienttest=true): lines mobs up in front of the camera on a flat world,
 * shoots a few, kills them all, and screenshots the fall, the bodies at rest, every stage of decay
 * and close-ups from ground level, then quits.
 */
public final class ClientSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks = -1;
    private static int ground;
    private static final List<Mob> mobs = new ArrayList<>();
    private static final List<double[]> spots = new ArrayList<>();
    private static final List<String> names = new ArrayList<>();
    private static final java.util.Map<String, java.util.UUID> corpseOf = new java.util.HashMap<>();
    private static final List<Step> steps = new ArrayList<>();
    private static boolean planned;

    private record Step(int at, Runnable run) {
    }

    private ClientSelfTest() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (!planned) {
            planned = true;
            plan();
        }
        for (Step s : new ArrayList<>(steps)) {
            if (s.at <= ticks) {
                steps.remove(s);
                try {
                    s.run.run();
                } catch (Throwable t) {
                    LOG.error("CLIENTTEST step failed", t);
                }
            }
        }
    }

    private static void at(int t, Runnable r) {
        steps.add(new Step(t, r));
    }

    private static void server(Runnable r) {
        Minecraft.getInstance().getSingleplayerServer().execute(r);
    }

    private static ServerLevel level() {
        return Minecraft.getInstance().getSingleplayerServer().overworld();
    }

    private static ServerPlayer player() {
        return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
    }

    private static void camera(double x, double y, double z, float yaw, float pitch) {
        server(() -> {
            ServerPlayer p = player();
            p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(level(), x, y, z, yaw, pitch);
        });
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = true;
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> LOG.info("CLIENTTEST screenshot {}", name));
    }

    private static void plan() {
        int start = 60;
        at(start, () -> server(ClientSelfTest::setup));
        at(start + 30, () -> server(ClientSelfTest::shootSome));
        at(start + 50, () -> shot("00_alive"));
        at(start + 55, () -> server(ClientSelfTest::killAll));
        at(start + 58, () -> server(ClientSelfTest::matchCorpses));
        at(start + 60, () -> shot("01_falling"));
        at(start + 65, () -> shot("02_falling"));
        at(start + 72, () -> shot("03_falling"));
        at(start + 160, () -> shot("04_at_rest"));
        at(start + 161, ClientSelfTest::logLimbs);
        float[] stages = {0.1F, 0.22F, 0.36F, 0.5F, 0.64F, 0.78F, 0.9F, 1.0F};
        int t = start + 170;
        for (float f : stages) {
            final float frac = f;
            at(t, () -> server(() -> ageAll(frac)));
            at(t + 12, () -> shot(String.format("05_decay_%03d", Math.round(frac * 100))));
            t += 20;
        }
        // close-ups from low down: fresh, rotting, skeleton
        String[] who = {"bog_villager", "bog_cow", "ice_villager", "ice_wolf", "cow_drowned", "zombie_frozen", "cow_exploded", "zombie_withered", "zombie_fell", "cow_crushed",
                "remains_adventurer", "remains_mossy", "remains_piglin", "remains_ender", "remains_bleached", "remains_cave", "zombie_burnt", "cow_burnt", "zombie_armour", "zombie", "villager", "pillager", "cow", "sheep", "horse", "pig", "chicken", "wolf", "skeleton"};
        float[] when = {0.0F, 0.5F, 1.0F};
        int n = 10;
        for (String w : who) {
            float[] times = w.startsWith("bog_") || w.startsWith("ice_") ? new float[] {0.25F, 0.6F, 1.0F}
                    : w.endsWith("_burnt") ? new float[] {0.75F, 0.88F, 1.0F}
                    : w.startsWith("remains") ? new float[] {1.0F}
                    : w.endsWith("_withered") ? new float[] {0.6F, 1.0F}
                    : (w.endsWith("_drowned") || w.endsWith("_frozen") || w.endsWith("_exploded") || w.endsWith("_fell") || w.endsWith("_crushed"))
                    ? new float[] {0.15F, 0.6F} : when;
            for (float f : times) {
                final String name = String.format("%02d_%s_%03d", n, w, Math.round(f * 100));
                final float frac = f;
                at(t, () -> closeUp(w, frac));
                at(t + 22, () -> shot(name));
                t += 26;
            }
            n++;
        }
        at(t, () -> camera(1.0, ground + 4.5, 7.5, 0F, 30F));
        at(t + 30, () -> shot("91_remains_row"));
        t += 35;
        for (int col = 0; col < GALLERY_PER_ROW; col++) {
            final double cx = (col - GALLERY_PER_ROW / 2) * GALLERY_STEP + 0.5;
            final String name = String.format("92_gallery_%d", col);
            at(t, () -> camera(cx, ground + 10.5, GALLERY_Z - 15.5, 0F, 0F));
            at(t + 40, () -> shot(name));
            t += 45;
        }
        at(t, () -> camera(-20.5, ground + 4.0, 21.0, 0F, 8F));
        at(t + 40, () -> shot("93_small_fossils"));
        at(t + 45, () -> camera(-28.0, ground + 2.5, 25.0, 0F, 25F));
        at(t + 85, () -> shot("94_finds"));
        at(t + 90, () -> camera(28.5, ground + 7.0, 22.0, 0F, 50F));
        at(t + 130, () -> shot("95_trail"));
        at(t + 135, () -> camera(0.5, ground + 8.0, 34.0, 0F, 40F));
        at(t + 175, () -> shot("96_dig_site"));
        at(t + 180, () -> camera(49.5, ground + 2.5, 23.5, 0F, 5F));
        at(t + 220, () -> shot("97_cambrian_and_paintings"));
        at(t + 225, () -> camera(10.5, ground + 22.0, 92.0, 0F, 35F));
        at(t + 270, () -> shot("98_stone_age_sites"));
        at(t + 275, () -> camera(0.5, ground + 12.0, 113.0, 0F, 38F));
        at(t + 315, () -> shot("99_stone_circle"));
        at(t + 320, () -> camera(44.5, ground + 9.0, 111.0, 0F, 42F));
        at(t + 360, () -> shot("99_bone_hut"));
        at(t + 365, () -> camera(22.5, ground + 3.0, 112.0, 0F, 12F));
        at(t + 405, () -> shot("99_dolmen"));
        at(t + 410, () -> camera(-22.5, ground + 7.0, 108.0, 0F, 25F));
        at(t + 450, () -> shot("99_barrow"));
        at(t + 455, () -> camera(66.5, ground + 8.0, 114.0, 0F, 40F));
        at(t + 495, () -> shot("99_tar_pit"));
        t += 500;
        var forms = com.harold.corpsedecay.world.FormationFeature.Formation.values();
        for (int i = 0; i < forms.length; i++) {
            final double fx = FORMATION_X0 + i * FORMATION_STEP + 0.5;
            final String name = String.format("A%d_formation_%s", i, forms[i].id());
            at(t, () -> camera(fx, ground + 10.5, FORMATION_Z - 17.0, 0F, 2F));
            at(t + 40, () -> shot(name));
            t += 45;
        }
        // mummies, whale falls, stone whales
        at(t, () -> camera(4.0, ground + 5.5, -26.0, 180F, 28F));
        at(t + 40, () -> shot("B0_mummies"));
        at(t + 45, () -> camera(-2.5, ground + 2.2, -36.0, 160F, 25F));
        at(t + 85, () -> shot("B1_bog_bodies"));
        at(t + 90, () -> camera(15.5, ground + 2.6, -36.0, 200F, 25F));
        at(t + 130, () -> shot("B2_ice_mummies"));
        at(t + 135, () -> camera(-100.0, ground + 30.0, -168.0, 0F, 45F));
        at(t + 185, () -> shot("B3_whale_fall_above"));
        at(t + 190, () -> server(() -> player().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION, 2000, 0, false, false))));
        at(t + 192, () -> camera(-124.0, ground + 6.0, -136.0, -55F, 18F));
        at(t + 260, () -> shot("B4_whale_fall_under"));
        at(t + 262, () -> camera(-80.0, ground + 6.0, -100.5, 150F, 15F));
        at(t + 330, () -> shot("B5_whale_fall_sperm"));
        at(t + 335, () -> server(() -> player().removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)));
        at(t + 337, () -> camera(85.0, ground + 16.0, -168.0, 0F, 38F));
        at(t + 380, () -> shot("B6_desert_whales"));
        at(t + 385, () -> camera(DESERT_X - 9.0, ground + 4.0, DESERT_Z - 9.0, -45F, 22F));
        at(t + 425, () -> shot("B7_desert_whale_close"));
        t += 430;
        at(t + 5, () -> {
            LOG.info("CLIENTTEST DONE");
            Minecraft.getInstance().stop();
        });
    }

    private static void setup() {
        ServerLevel level = level();
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(18000 - 12000); // noon
        level.setWeatherParameters(6000, 0, false, false);
        ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        AABB area = new AABB(-200, -100, -200, 200, 400, 200);
        for (Entity e : level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer))) e.discard();

        // front row
        spawn("cow", EntityType.COW, -6, 0);
        Sheep sheep = (Sheep) spawn("sheep", EntityType.SHEEP, -3.6, 0);
        sheep.setColor(DyeColor.RED);
        Mob zombie = spawn("zombie_armour", EntityType.ZOMBIE, -1.2, 0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setDropChance(EquipmentSlot.CHEST, 2F);
        Villager villager = (Villager) spawn("villager", EntityType.VILLAGER, 1.2, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setType(VillagerType.PLAINS).setLevel(2));
        Horse horse = (Horse) spawn("horse", EntityType.HORSE, 3.8, 0);
        CompoundTag h = new CompoundTag();
        horse.addAdditionalSaveData(h);
        h.put("SaddleItem", new ItemStack(Items.SADDLE).save(new CompoundTag()));
        h.put("ArmorItem", new ItemStack(Items.IRON_HORSE_ARMOR).save(new CompoundTag()));
        h.putBoolean("Tame", true);
        horse.readAdditionalSaveData(h);
        Mob skeleton = spawn("skeleton", EntityType.SKELETON, 6.4, 0);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        // back row
        spawn("pig", EntityType.PIG, -6, 4);
        spawn("chicken", EntityType.CHICKEN, -3.6, 4);
        spawn("zombie", EntityType.ZOMBIE, -1.2, 4);
        Mob pillager = spawn("pillager", EntityType.PILLAGER, 1.2, 4);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        spawn("spider", EntityType.SPIDER, 3.8, 4);
        spawn("wolf", EntityType.WOLF, 6.4, 4);
        // these two die burning
        spawn("zombie_burnt", EntityType.ZOMBIE, 8.8, 0);
        spawn("cow_burnt", EntityType.COW, 8.8, 4);
        // a row dying other ways; the drowned cow in a pool so it floats
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(-6 + dx, ground - 1, 8 + dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(-6 + dx, ground - 2, 8 + dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            }
        }
        spawn("cow_drowned", EntityType.COW, -6, 8);
        spawn("zombie_frozen", EntityType.ZOMBIE, -3, 8);
        spawn("cow_exploded", EntityType.COW, 0, 8);
        spawn("zombie_withered", EntityType.ZOMBIE, 3, 8);
        spawn("zombie_fell", EntityType.ZOMBIE, 6, 8);
        spawn("cow_crushed", EntityType.COW, 9, 8);
        // old remains, weathered different ways, with gear and things growing on them
        CorpseEntity r = remains("remains_adventurer", EntityType.SKELETON, -7, net.minecraft.world.level.block.Blocks.GRASS_BLOCK, CorpseEntity.OLD, 0.05);
        if (r != null) {
            r.giveGear(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            r.giveGear(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            r.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            r.addOldArrow(level.random);
            r.addOldArrow(level.random);
            r.addGrowth(net.minecraft.world.level.block.Blocks.GRASS, 0.55F, level.random);
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(-8, ground, 13), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        }
        r = remains("remains_mossy", EntityType.COW, -4, net.minecraft.world.level.block.Blocks.MOSS_BLOCK, CorpseEntity.MOSSY, 0.05);
        if (r != null) {
            r.addGrowth(net.minecraft.world.level.block.Blocks.MOSS_CARPET, 0.45F, level.random);
            r.addGrowth(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM, 0.6F, level.random);
            r.addGrowth(net.minecraft.world.level.block.Blocks.RED_MUSHROOM, 0.6F, level.random);
        }
        r = remains("remains_piglin", EntityType.PIGLIN, -1, net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM, CorpseEntity.CHARRED, 0.05);
        if (r != null) {
            r.giveGear(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            r.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            r.addGrowth(net.minecraft.world.level.block.Blocks.CRIMSON_FUNGUS, 0.6F, level.random);
        }
        remains("remains_ender", EntityType.ENDERMAN, 2, net.minecraft.world.level.block.Blocks.END_STONE, CorpseEntity.ENDER, 0.05);
        r = remains("remains_bleached", EntityType.ZOMBIE, 5, net.minecraft.world.level.block.Blocks.SAND, CorpseEntity.BLEACHED, 0.3);
        if (r != null) r.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        r = remains("remains_cave", EntityType.SKELETON, 8, net.minecraft.world.level.block.Blocks.DEEPSLATE, CorpseEntity.DARK, 0.05);
        if (r != null) {
            r.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            r.giveGear(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            r.addGrowth(net.minecraft.world.level.block.Blocks.COBWEB, 0.9F, level.random);
        }
        fossilGallery(level);
        mummiesAndWhales(level);
        camera(0.5, ground + 6.5, -8.5, 0F, 32F);
    }

    static final int DESERT_X = 85, DESERT_Z = -125;

    /**
     * A strip of swamp and one of snow where animals and villagers die and mummify; old bog bodies
     * in mud and a pool; frozen mummies in snow and ice; a tank of sea with a whale fall in it; a
     * patch of desert with stone whales.
     */
    private static void mummiesAndWhales(ServerLevel level) {
        var src = level.getServer().createCommandSourceStack().withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(src, "fillbiome -10 " + (ground - 6) + " -48 3 " + (ground + 10) + " -14 minecraft:swamp");
        level.getServer().getCommands().performPrefixedCommand(src, "fillbiome 4 " + (ground - 6) + " -48 20 " + (ground + 10) + " -14 minecraft:snowy_plains");
        for (int x = 4; x <= 20; x++) {
            for (int z = -48; z <= -14; z++) level.setBlock(new net.minecraft.core.BlockPos(x, ground - 1, z), net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState(), 2);
        }
        spawn("bog_villager", EntityType.VILLAGER, -7, -18);
        spawn("bog_cow", EntityType.COW, -3, -18);
        spawn("bog_horse", EntityType.HORSE, 1, -18);
        Mob iceMan = spawn("ice_villager", EntityType.VILLAGER, 8, -18);
        iceMan.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        spawn("ice_wolf", EntityType.WOLF, 12, -18);
        spawn("ice_cow", EntityType.COW, 16, -18);
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(1234);
        // old bog bodies: two in the mud, one under a pool
        for (int x = -6; x <= 0; x++) {
            for (int z = -44; z <= -40; z++) {
                level.setBlock(new net.minecraft.core.BlockPos(x, ground - 1, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(new net.minecraft.core.BlockPos(x, ground - 2, z), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        com.harold.corpsedecay.world.MummyFeature.bogBody(level, new net.minecraft.core.BlockPos(-3, ground - 2, -42), r);
        com.harold.corpsedecay.world.MummyFeature.bogBody(level, new net.minecraft.core.BlockPos(-8, ground - 1, -34), r);
        com.harold.corpsedecay.world.MummyFeature.bogBody(level, new net.minecraft.core.BlockPos(1, ground - 1, -32), r);
        // frozen: two locked in ice, one in the snow
        com.harold.corpsedecay.world.MummyFeature.iceMummy(level, new net.minecraft.core.BlockPos(9, ground - 1, -42), r, true);
        com.harold.corpsedecay.world.MummyFeature.iceMummy(level, new net.minecraft.core.BlockPos(15, ground - 1, -42), r, true);
        com.harold.corpsedecay.world.MummyFeature.iceMummy(level, new net.minecraft.core.BlockPos(12, ground - 1, -34), r, false);

        // a tank of sea with a whale fall on its floor
        int x0 = -132, x1 = -68, z0 = -152, z1 = -98, depth = 12;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                level.setBlock(new net.minecraft.core.BlockPos(x, ground - 1, z), (wall ? net.minecraft.world.level.block.Blocks.GLASS
                        : (x + z) % 7 == 0 ? net.minecraft.world.level.block.Blocks.GRAVEL : net.minecraft.world.level.block.Blocks.SAND).defaultBlockState(), 2);
                for (int y = 0; y < depth; y++) {
                    level.setBlock(new net.minecraft.core.BlockPos(x, ground + y, z), (wall ? net.minecraft.world.level.block.Blocks.GLASS
                            : net.minecraft.world.level.block.Blocks.WATER).defaultBlockState(), 2);
                }
            }
        }
        com.harold.corpsedecay.world.WhaleFeature.seaWhale(level, new net.minecraft.core.BlockPos(-108, ground - 1, -126), net.minecraft.util.RandomSource.create(3),
                com.harold.corpsedecay.world.WhaleFeature.Kind.BLUE, net.minecraft.core.Direction.EAST);
        com.harold.corpsedecay.world.WhaleFeature.seaWhale(level, new net.minecraft.core.BlockPos(-88, ground - 1, -110), net.minecraft.util.RandomSource.create(4),
                com.harold.corpsedecay.world.WhaleFeature.Kind.SPERM, net.minecraft.core.Direction.WEST);
        com.harold.corpsedecay.world.WhaleFeature.seaWhale(level, new net.minecraft.core.BlockPos(-92, ground - 1, -135), net.minecraft.util.RandomSource.create(5),
                com.harold.corpsedecay.world.WhaleFeature.Kind.HUMPBACK, net.minecraft.core.Direction.NORTH);

        // a patch of desert with stone whales and wind-carved rocks
        level.getServer().getCommands().performPrefixedCommand(src, "fillbiome 55 " + (ground - 6) + " -155 115 " + (ground + 20) + " -95 minecraft:desert");
        for (int x = 55; x <= 115; x++) {
            for (int z = -155; z <= -95; z++) {
                level.setBlock(new net.minecraft.core.BlockPos(x, ground - 1, z), net.minecraft.world.level.block.Blocks.SAND.defaultBlockState(), 2);
                level.setBlock(new net.minecraft.core.BlockPos(x, ground - 2, z), net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState(), 2);
                level.setBlock(new net.minecraft.core.BlockPos(x, ground - 3, z), net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState(), 2);
            }
        }
        com.harold.corpsedecay.world.WhaleFeature.desertWhale(level, new net.minecraft.core.BlockPos(DESERT_X, ground - 1, DESERT_Z), net.minecraft.util.RandomSource.create(8),
                net.minecraft.core.Direction.EAST);
        com.harold.corpsedecay.world.WhaleFeature.valley(level, new net.minecraft.core.BlockPos(DESERT_X, ground, DESERT_Z - 2), net.minecraft.util.RandomSource.create(9));
    }

    static final int GALLERY_Z = 80, GALLERY_STEP = 22, GALLERY_PER_ROW = 9;

    /**
     * A long wall with every kind of fossil skeleton in it, two rows high, each in a different rock;
     * a display of all the small fossils and the other finds; a footprint trail; a dig site.
     */
    private static void fossilGallery(ServerLevel level) {
        net.minecraft.world.level.block.Block[] rocks = {net.minecraft.world.level.block.Blocks.STONE, net.minecraft.world.level.block.Blocks.DEEPSLATE,
                net.minecraft.world.level.block.Blocks.SANDSTONE, net.minecraft.world.level.block.Blocks.RED_SANDSTONE,
                net.minecraft.world.level.block.Blocks.TERRACOTTA};
        String[][] all = com.harold.corpsedecay.block.FossilShapes.SKELETONS;
        for (int i = 0; i < all.length; i++) {
            int col = i % GALLERY_PER_ROW, row = i / GALLERY_PER_ROW;
            int cx = (col - GALLERY_PER_ROW / 2) * GALLERY_STEP;
            int base = ground + row * 10;
            var rock = rocks[i % rocks.length].defaultBlockState();
            for (int x = cx - GALLERY_STEP / 2; x < cx + GALLERY_STEP / 2; x++) {
                for (int y = 0; y < 10; y++) level.setBlock(new net.minecraft.core.BlockPos(x, base + y, GALLERY_Z), rock, 2);
            }
            com.harold.corpsedecay.world.FossilBedFeature.placeSkeleton(level, new net.minecraft.core.BlockPos(cx, base + 5, GALLERY_Z), level.random,
                    com.harold.corpsedecay.world.FossilBedFeature.named(all[i][0]), 0F, 0.5F,
                    i % 2 == 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST);
            LOG.info("CLIENTTEST gallery {} {} at x={} row {}", i, all[i][0], cx, row);
        }
        // every small fossil in every rock, and amber, petrified wood and eggs in front
        java.util.List<String> small = com.harold.corpsedecay.block.FossilBlocks.pieces(com.harold.corpsedecay.block.FossilBlocks.SMALL);
        com.harold.corpsedecay.block.FossilBlocks.Host[] hosts = com.harold.corpsedecay.block.FossilBlocks.Host.values();
        for (int h = 0; h < hosts.length; h++) {
            for (int k = 0; k < small.size(); k++) {
                com.harold.corpsedecay.block.FossilBlocks.place(level, new net.minecraft.core.BlockPos(-30 + k, ground + hosts.length - 1 - h, 30), hosts[h], small.get(k), net.minecraft.core.Direction.NORTH);
            }
        }
        int ex = -30;
        for (net.minecraft.world.level.block.Block b : com.harold.corpsedecay.block.FossilBlocks.extras()) {
            level.setBlock(new net.minecraft.core.BlockPos(ex, ground, 28), b.defaultBlockState(), 2);
            ex += 2;
        }
        level.setBlock(new net.minecraft.core.BlockPos(ex, ground, 28), com.harold.corpsedecay.block.FossilBlocks.FOSSIL_EGG.get().defaultBlockState()
                .setValue(com.harold.corpsedecay.block.FossilEggBlock.EGGS, 3), 2);
        // skull pieces of every kind, in a column of each rock
        java.util.List<String> bones = com.harold.corpsedecay.block.FossilBlocks.pieces(com.harold.corpsedecay.block.FossilBlocks.BONE);
        for (int k = 0; k < bones.size(); k++) {
            com.harold.corpsedecay.block.FossilBlocks.place(level, new net.minecraft.core.BlockPos(-30 + k, ground + 6, 30), hosts[k % hosts.length], bones.get(k), net.minecraft.core.Direction.EAST);
        }
        // a trail across bare rock
        for (int x = 20; x <= 36; x++) {
            for (int z = 24; z <= 34; z++) level.setBlock(new net.minecraft.core.BlockPos(x, ground - 1, z), rocks[4].defaultBlockState(), 2);
        }
        com.harold.corpsedecay.world.FootprintTrailFeature.trail(level, new net.minecraft.core.BlockPos(21, ground - 1, 29), level.random,
                net.minecraft.core.Direction.EAST, 8);
        // a dig site
        com.harold.corpsedecay.world.DigSiteFeature.dig(level, new net.minecraft.core.BlockPos(0, ground, 42), level.random,
                net.minecraft.core.Direction.EAST, false);
        // Cambrian creatures in deepslate and stone over a wall of cave paintings
        java.util.List<String> cambrian = com.harold.corpsedecay.block.FossilBlocks.pieces(com.harold.corpsedecay.block.FossilBlocks.CAMBRIAN);
        for (int x = 44; x <= 56; x++) {
            for (int y = 0; y < 5; y++) level.setBlock(new net.minecraft.core.BlockPos(x, ground + y, 31), rocks[0].defaultBlockState(), 2);
        }
        for (int k = 0; k < cambrian.size(); k++) {
            var d = cambrian.get(k).startsWith("anomalocaris") ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.NORTH;
            com.harold.corpsedecay.block.FossilBlocks.place(level, new net.minecraft.core.BlockPos(55 - k, ground + 4, 30), com.harold.corpsedecay.block.FossilBlocks.Host.DEEPSLATE, cambrian.get(k), d);
            com.harold.corpsedecay.block.FossilBlocks.place(level, new net.minecraft.core.BlockPos(55 - k, ground + 3, 30), com.harold.corpsedecay.block.FossilBlocks.Host.STONE, cambrian.get(k), d);
        }
        java.util.List<String> paintings = com.harold.corpsedecay.block.FossilBlocks.pieces(com.harold.corpsedecay.block.FossilBlocks.PAINTING);
        for (int k = 0; k < paintings.size(); k++) {
            for (int y = 0; y < 2; y++) {
                com.harold.corpsedecay.block.FossilBlocks.place(level, new net.minecraft.core.BlockPos(53 - k, ground + y, 30), 
                        y == 0 ? com.harold.corpsedecay.block.FossilBlocks.Host.SANDSTONE : com.harold.corpsedecay.block.FossilBlocks.Host.STONE,
                        paintings.get((k + y * 3) % paintings.size()), net.minecraft.core.Direction.NORTH);
            }
        }
        // Stone Age sites, beyond the gallery wall
        net.minecraft.util.RandomSource nr = net.minecraft.util.RandomSource.create(42);
        com.harold.corpsedecay.world.NeolithicFeature.stoneCircle(level, new net.minecraft.core.BlockPos(0, ground, 130), nr, false);
        com.harold.corpsedecay.world.NeolithicFeature.dolmen(level, new net.minecraft.core.BlockPos(22, ground, 118), nr);
        com.harold.corpsedecay.world.NeolithicFeature.barrow(level, new net.minecraft.core.BlockPos(-22, ground, 120), nr, net.minecraft.core.Direction.EAST);
        com.harold.corpsedecay.world.NeolithicFeature.boneHut(level, new net.minecraft.core.BlockPos(44, ground, 120), nr, net.minecraft.core.Direction.NORTH);
        com.harold.corpsedecay.world.TarPitFeature.pit(level, new net.minecraft.core.BlockPos(66, ground, 122), nr);
        // every formation, laid into a block of stone and cut through to show its layers
        var forms = com.harold.corpsedecay.world.FormationFeature.Formation.values();
        for (int i = 0; i < forms.length; i++) {
            int cx = FORMATION_X0 + i * FORMATION_STEP, cy = ground + 10, cz = FORMATION_Z;
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(cx - 12, ground, cz - 12, cx + 12, ground + 20, cz + 12)) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            com.harold.corpsedecay.world.FormationFeature.lay(level, new net.minecraft.core.BlockPos(cx, cy, cz), net.minecraft.util.RandomSource.create(100 + i),
                    forms[i], 11, 7, 11);
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(cx - 12, ground, cz - 12, cx + 12, ground + 20, cz - 1)) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            LOG.info("CLIENTTEST formation {} at x={}", forms[i].id(), cx);
        }
    }

    static final int FORMATION_X0 = -150, FORMATION_STEP = 30, FORMATION_Z = 170;

    private static CorpseEntity remains(String name, EntityType<?> type, int x, net.minecraft.world.level.block.Block rock, int material, double sink) {
        ServerLevel level = level();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, ground - 1, 13 + dz), rock.defaultBlockState());
        }
        net.minecraft.world.phys.Vec3 top = new net.minecraft.world.phys.Vec3(x + 0.5, ground, 13.5);
        CorpseEntity f = CorpseEntity.remains(level, type, top, level.random, material, sink);
        if (f == null) return null;
        if (!level.addFreshEntity(f)) return null;
        corpseOf.put(name, f.getUUID());
        return f;
    }

    private static Mob spawn(String name, EntityType<? extends Mob> type, double x, double z) {
        Mob m = type.create(level());
        m.moveTo(x + 0.5, ground, z + 0.5, 180F, 0F);
        m.setYBodyRot(180F);
        m.setYHeadRot(180F);
        m.setNoAi(true);
        m.setPersistenceRequired();
        m.setInvulnerable(false);
        level().addFreshEntity(m);
        mobs.add(m);
        names.add(name);
        spots.add(new double[] {x + 0.5, z + 0.5});
        return m;
    }

    /** Arrows into a few of them from the camera's side. */
    private static void shootSome() {
        for (int k = 0; k < mobs.size(); k++) {
            Mob m = mobs.get(k);
            String id = names.get(k);
            int count = id.startsWith("zombie") ? 3 : (id.equals("villager") || id.equals("cow") || id.equals("pillager") ? 1 : 0);
            for (int i = 0; i < count; i++) {
                Arrow a = new Arrow(level(), m.getX() + (i - 1) * 0.15, m.getY() + m.getBbHeight() * (0.5 + 0.12 * i), m.getZ() - 3);
                a.shoot(0, 0.04, 1, 2.2F, 0F);
                a.setBaseDamage(0.4);
                a.pickup = AbstractArrow.Pickup.ALLOWED;
                level().addFreshEntity(a);
            }
        }
    }

    private static void killAll() {
        ServerPlayer p = player();
        p.setGameMode(GameType.CREATIVE);
        for (int k = 0; k < mobs.size(); k++) {
            Mob m = mobs.get(k);
            if (!m.isAlive()) continue;
            m.invulnerableTime = 0;
            String n = names.get(k);
            var ds = level().damageSources();
            if (n.endsWith("_burnt")) {
                m.setSecondsOnFire(5);
                m.hurt(ds.onFire(), 1000F);
            } else if (n.endsWith("_drowned") || n.endsWith("_frozen") || n.endsWith("_exploded") || n.endsWith("_withered")
                    || n.endsWith("_fell") || n.endsWith("_crushed")) {
                m.clearFire();
                m.hurt(n.endsWith("_drowned") ? ds.drown() : n.endsWith("_frozen") ? ds.freeze() : n.endsWith("_exploded") ? ds.explosion(null, null)
                        : n.endsWith("_withered") ? ds.wither() : n.endsWith("_fell") ? ds.fall() : ds.anvil(null), 1000F);
            } else {
                m.clearFire(); // undead catch fire in the noon sun; these should die normally
                m.hurt(level().damageSources().playerAttack(p), 1000F);
            }
        }
        p.setGameMode(GameType.SPECTATOR);
    }

    private static void matchCorpses() {
        ServerLevel level = level();
        for (int k = 0; k < mobs.size(); k++) {
            Mob m = mobs.get(k);
            String type = EntityType.getKey(m.getType()).toString();
            CorpseEntity best = null;
            double bd = Double.MAX_VALUE;
            for (CorpseEntity c : level.getEntitiesOfClass(CorpseEntity.class, new AABB(-50, -100, -50, 50, 400, 50))) {
                if (!c.mobType().equals(type) || c.kind() == com.harold.corpsedecay.CorpseKind.REMAINS) continue;
                double d = c.position().distanceToSqr(m.position());
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
            if (best != null) corpseOf.putIfAbsent(names.get(k), best.getUUID());
        }
        LOG.info("CLIENTTEST matched {} corpses", corpseOf.size());
    }

    private static void ageAll(float frac) {
        ServerLevel level = level();
        for (CorpseEntity c : level.getEntitiesOfClass(CorpseEntity.class, new AABB(-50, -100, -50, 50, 400, 50))) {
            if (c.kind() == com.harold.corpsedecay.CorpseKind.REMAINS) continue;
            c.setDeathTime(level.getGameTime() - Math.round(frac * c.decayTicks()));
        }
    }

    private static void logLimbs() {
        Minecraft mc = Minecraft.getInstance();
        LOG.info("CLIENTTEST ground y={}", ground);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CorpseEntity c) {
                LOG.info("CLIENTTEST limbs {} at {} settled={} :: {}", c.getId(), c.position(), c.settled(), CorpseClient.of(c).debug(c));
            }
        }
    }

    private static CorpseEntity corpseFor(ServerLevel level, String name) {
        java.util.UUID id = corpseOf.get(name);
        if (id != null && level.getEntity(id) instanceof CorpseEntity c) return c;
        int idx = names.indexOf(name);
        if (idx < 0) return null;
        double[] spot = spots.get(idx);
        String type = EntityType.getKey(mobs.get(idx).getType()).toString();
        CorpseEntity best = null;
        double bd = Double.MAX_VALUE;
        for (CorpseEntity c : level.getEntitiesOfClass(CorpseEntity.class, new AABB(-50, -100, -50, 50, 400, 50))) {
            if (!c.mobType().equals(type)) continue;
            double d = (c.getX() - spot[0]) * (c.getX() - spot[0]) + (c.getZ() - spot[1]) * (c.getZ() - spot[1]);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    /** A low three-quarter view of one body. */
    private static void closeUp(String name, float frac) {
        server(() -> {
            ServerLevel level = level();
            CorpseEntity c = corpseFor(level, name);
            if (c == null) {
                LOG.warn("CLIENTTEST no corpse for {}", name);
                return;
            }
            if (c.kind() != com.harold.corpsedecay.CorpseKind.REMAINS) c.setDeathTime(level.getGameTime() - Math.round(frac * c.decayTicks()));
            double size = Math.max(1.0, Math.max(c.bodyDepth(), c.bodyHeight()));
            ServerPlayer p = player();
            p.setGameMode(GameType.SPECTATOR);
            double dx = -0.7 * size * 1.25, dz = -1.2 * size * 1.25, dy = 0.8 * size;
            float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            // the camera is at eye height above where the player stands
            p.teleportTo(level, c.getX() + dx, c.getY() + dy - p.getEyeHeight(), c.getZ() + dz, yaw, pitch);
        });
    }
}

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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * CI only (-Dcorpsedecay.clienttest=true): lines mobs up in front of the camera on a flat world,
 * shoots a few, kills them all, and screenshots the fall, the bodies at rest, every stage of decay
 * and some close-ups, then quits.
 */
public final class ClientSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks = -1;
    private static int start;
    private static int ground;
    private static final List<Mob> mobs = new ArrayList<>();
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
        start = 60;
        at(start, () -> server(ClientSelfTest::setup));
        at(start + 30, () -> server(ClientSelfTest::shootSome));
        at(start + 50, () -> shot("00_alive"));
        at(start + 55, () -> server(ClientSelfTest::killAll));
        at(start + 58, () -> shot("01_falling"));
        at(start + 63, () -> shot("02_falling"));
        at(start + 70, () -> shot("03_falling"));
        at(start + 160, () -> shot("04_at_rest"));
        float[] stages = {0.1F, 0.22F, 0.36F, 0.5F, 0.64F, 0.78F, 0.9F, 1.0F};
        int t = start + 170;
        for (float f : stages) {
            final float frac = f;
            at(t, () -> server(() -> age(frac)));
            at(t + 12, () -> shot(String.format("05_decay_%03d", Math.round(frac * 100))));
            t += 20;
        }
        // close-ups of the skeletons with their gear, then fresh bodies with arrows in them
        at(t, () -> closeUp("minecraft:zombie", 1.0F));
        at(t + 25, () -> shot("10_zombie_skeleton_armour"));
        at(t + 30, () -> closeUp("minecraft:villager", 1.0F));
        at(t + 55, () -> shot("11_villager_skeleton_clothes"));
        at(t + 60, () -> closeUp("minecraft:horse", 1.0F));
        at(t + 85, () -> shot("12_horse_skeleton"));
        at(t + 90, () -> closeUp("minecraft:cow", 1.0F));
        at(t + 115, () -> shot("13_cow_skeleton"));
        at(t + 120, () -> closeUp("minecraft:zombie", 0.0F));
        at(t + 145, () -> shot("14_zombie_fresh_arrows"));
        at(t + 150, () -> closeUp("minecraft:zombie", 0.45F));
        at(t + 175, () -> shot("15_zombie_rotting"));
        at(t + 180, () -> closeUp("minecraft:sheep", 0.25F));
        at(t + 205, () -> shot("16_sheep_bloated"));
        at(t + 210, () -> closeUp("minecraft:chicken", 1.0F));
        at(t + 235, () -> shot("17_chicken_skeleton"));
        at(t + 245, () -> {
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
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        for (CorpseEntity c : level.getEntitiesOfClass(CorpseEntity.class, new AABB(-200, -100, -200, 200, 400, 200))) c.discard();

        Mob zombie = spawn(EntityType.ZOMBIE, 0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setDropChance(EquipmentSlot.CHEST, 2F);
        Villager villager = (Villager) spawn(EntityType.VILLAGER, 2.4);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setType(VillagerType.PLAINS).setLevel(2));
        Sheep sheep = (Sheep) spawn(EntityType.SHEEP, -2.4);
        sheep.setColor(DyeColor.RED);
        spawn(EntityType.COW, -5);
        spawn(EntityType.CHICKEN, 4.6);
        Horse horse = (Horse) spawn(EntityType.HORSE, 7.2);
        CompoundTag h = new CompoundTag();
        horse.addAdditionalSaveData(h);
        h.put("SaddleItem", new ItemStack(Items.SADDLE).save(new CompoundTag()));
        h.put("ArmorItem", new ItemStack(Items.IRON_HORSE_ARMOR).save(new CompoundTag()));
        h.putBoolean("Tame", true);
        horse.readAdditionalSaveData(h);
        Mob skeleton = spawn(EntityType.SKELETON, -7.4);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Mob spider = spawn(EntityType.SPIDER, -9.8);
        spider.moveTo(-9.8 + 0.5, ground, 0.5, 180, 0);
        spawn(EntityType.PIG, 9.8);
        camera(0.5, ground + 4.2, -10.5, 0F, 20F);
    }

    private static Mob spawn(EntityType<? extends Mob> type, double x) {
        Mob m = type.create(level());
        m.moveTo(x + 0.5, ground, 0.5, 180F, 0F);
        m.setYBodyRot(180F);
        m.setYHeadRot(180F);
        m.setNoAi(true);
        m.setPersistenceRequired();
        level().addFreshEntity(m);
        mobs.add(m);
        return m;
    }

    /** Arrows into the zombie, villager and cow from the camera's side. */
    private static void shootSome() {
        for (Mob m : mobs) {
            String id = EntityType.getKey(m.getType()).toString();
            int count = id.equals("minecraft:zombie") ? 3 : (id.equals("minecraft:villager") || id.equals("minecraft:cow") ? 1 : 0);
            for (int i = 0; i < count; i++) {
                Arrow a = new Arrow(level(), m.getX() + (i - 1) * 0.15, m.getY() + m.getBbHeight() * (0.55 + 0.15 * i), m.getZ() - 4);
                a.shoot(0, 0.05, 1, 2.2F, 0F);
                a.setBaseDamage(0.5);
                a.pickup = AbstractArrow.Pickup.ALLOWED;
                level().addFreshEntity(a);
            }
        }
    }

    private static void killAll() {
        ServerPlayer p = player();
        p.setGameMode(GameType.CREATIVE);
        for (Mob m : mobs) {
            if (!m.isAlive()) continue;
            m.invulnerableTime = 0;
            m.hurt(level().damageSources().playerAttack(p), 1000F);
        }
        p.setGameMode(GameType.SPECTATOR);
    }

    private static void age(float frac) {
        ServerLevel level = level();
        for (CorpseEntity c : level.getEntitiesOfClass(CorpseEntity.class, new AABB(-50, -100, -50, 50, 400, 50))) {
            c.setDeathTime(level.getGameTime() - Math.round(frac * c.decayTicks()));
        }
    }

    private static void closeUp(String type, float frac) {
        server(() -> {
            ServerLevel level = level();
            for (CorpseEntity c : level.getEntitiesOfClass(CorpseEntity.class, new AABB(-50, -100, -50, 50, 400, 50))) {
                if (!c.mobType().equals(type)) continue;
                c.setDeathTime(level.getGameTime() - Math.round(frac * c.decayTicks()));
                Vec3 pos = c.position();
                double size = Math.max(1.2, Math.max(c.bodyWidth(), c.bodyHeight()));
                ServerPlayer p = player();
                p.setGameMode(GameType.SPECTATOR);
                p.teleportTo(level, pos.x - size * 0.9, pos.y + size * 1.1, pos.z - size * 1.2, -35F, 38F);
                return;
            }
        });
    }
}

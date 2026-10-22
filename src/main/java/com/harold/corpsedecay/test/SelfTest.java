package com.harold.corpsedecay.test;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.slf4j.Logger;

import com.harold.corpsedecay.CorpseKind;
import com.harold.corpsedecay.corpse.CorpseEntity;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Runs on a dedicated server in CI (-Dcorpsedecay.selftest=true): kills mobs and checks the corpses. */
public final class SelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static int tick;
    private static int failures;
    private static int passes;
    private static ServerLevel level;
    private static int groundY;
    private static Mob zombie;
    private static CorpseEntity zombieCorpse;
    private static final List<Mob> others = new ArrayList<>();
    private static int settleStart;
    private static boolean done;

    private SelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            passes++;
            LOG.info("SELFTEST ok   {}", what);
        } else {
            failures++;
            LOG.error("SELFTEST FAIL {}", what);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tick++;
        try {
            run(server);
        } catch (Throwable t) {
            LOG.error("SELFTEST crashed", t);
            failures++;
            finish(server);
        }
    }

    private static void run(MinecraftServer server) {
        switch (tick) {
            case 40 -> setup(server);
            case 45 -> shootZombie();
            case 60 -> {
                check(zombie.isAlive(), "zombie survives one arrow");
                check(zombie.getPersistentData().getList("corpsedecay_arrows", 10).size() == 1, "arrow hit is remembered");
                killOthers();
                zombie.hurt(level.damageSources().generic(), 1000F);
            }
            case 61 -> afterDeath();
            default -> {
            }
        }
        if (tick > 61 && settleStart > 0 && !done) {
            if (zombieCorpse.settled() || tick - settleStart > 400) {
                int took = tick - settleStart;
                settleStart = -1;
                LOG.info("SELFTEST settled after {} ticks", took);
                settled(server);
            }
        }
    }

    private static void setup(MinecraftServer server) {
        level = server.overworld();
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(6000);
        groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        LOG.info("SELFTEST ground at y={}", groundY);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) level.getChunk(new BlockPos(x * 4, groundY, z * 4));
        }
        zombie = spawn(EntityType.ZOMBIE, 0, 0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setDropChance(EquipmentSlot.HEAD, 0F);
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setDropChance(EquipmentSlot.CHEST, 2F);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setDropChance(EquipmentSlot.MAINHAND, 0F);
        others.add(spawn(EntityType.COW, 4, 0));
        others.add(spawn(EntityType.SPIDER, -4, 0));
        Mob skeleton = spawn(EntityType.SKELETON, 0, 4);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        others.add(skeleton);
        others.add(spawn(EntityType.VILLAGER, 4, 4));
        others.add(spawn(EntityType.IRON_GOLEM, -4, 4));
        Entity stand = EntityType.ARMOR_STAND.create(level);
        stand.moveTo(8, groundY, 8, 0, 0);
        level.addFreshEntity(stand);
        others.add(null);
        stand.hurt(level.damageSources().generic(), 1000F);
    }

    private static <T extends Mob> T spawn(EntityType<T> type, double x, double z) {
        T mob = type.create(level);
        mob.moveTo(x + 0.5, groundY, z + 0.5, 0, 0);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    private static void shootZombie() {
        Arrow arrow = new Arrow(level, zombie.getX(), zombie.getY() + 1.3, zombie.getZ() - 4);
        arrow.shoot(0, 0, 1, 2.0F, 0F);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        arrow.setBaseDamage(1.0);
        level.addFreshEntity(arrow);
    }

    private static void killOthers() {
        for (Mob m : others) {
            if (m != null) m.hurt(level.damageSources().generic(), 1000F);
        }
    }

    private static void afterDeath() {
        check(zombie.isRemoved(), "dead zombie is removed straight away (no vanilla poof)");
        List<CorpseEntity> corpses = corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20));
        LOG.info("SELFTEST corpses found: {}", corpses.size());
        for (CorpseEntity c : corpses) LOG.info("SELFTEST   {} at {}", c.mobType(), c.position());
        for (Entity en : level.getAllEntities()) {
            if (en instanceof CorpseEntity c) LOG.info("SELFTEST   (anywhere) {} at {} removed={}", c.mobType(), c.position(), c.isRemoved());
        }
        check(corpses.size() == 6, "six mobs leave six corpses (armour stand leaves none), got " + corpses.size());
        zombieCorpse = nearest(corpses, 0.5, 0.5);
        check(zombieCorpse != null && zombieCorpse.mobType().equals("minecraft:zombie"), "zombie corpse where the zombie died");
        if (zombieCorpse == null) {
            finish(level.getServer());
            return;
        }
        check(zombieCorpse.equipment(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "corpse still wears the chestplate");
        check(zombieCorpse.lootable(EquipmentSlot.CHEST), "chestplate it would have dropped can be taken off it");
        check(zombieCorpse.equipment(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "corpse still wears the helmet");
        check(!zombieCorpse.lootable(EquipmentSlot.HEAD), "helmet it wouldn't have dropped stays on");
        check(zombieCorpse.equipment(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "corpse still holds its sword");
        check(items(Items.DIAMOND_CHESTPLATE).isEmpty(), "chestplate didn't also drop on the ground");
        check(zombieCorpse.arrows().size() == 1, "the arrow is still in the body");
        check(!zombieCorpse.settled(), "body is still falling right after death");
        check(kindOf(corpses, "minecraft:cow") == CorpseKind.FLESH, "cow rots to a skeleton");
        check(kindOf(corpses, "minecraft:spider") == CorpseKind.NO_BONES, "spider has no skeleton");
        check(kindOf(corpses, "minecraft:skeleton") == CorpseKind.BONES, "skeleton is already bones");
        check(kindOf(corpses, "minecraft:iron_golem") == CorpseKind.NO_DECAY, "iron golem doesn't rot");
        CorpseEntity skel = byType(corpses, "minecraft:skeleton");
        check(skel != null && skel.equipment(EquipmentSlot.MAINHAND).is(Items.BOW), "skeleton's body keeps its bow");
        settleStart = tick;
    }

    private static void settled(MinecraftServer server) {
        check(zombieCorpse.settled(), "body comes to rest");
        Vector3f up = zombieCorpse.orientation().transform(new Vector3f(0, 1, 0));
        check(up.y < 0.6F, "zombie ends up lying down (up axis y=" + up.y + ")");
        check(zombieCorpse.getY() > groundY - 0.05 && zombieCorpse.getY() < groundY + 1.2, "body rests on the ground (y=" + zombieCorpse.getY() + ")");
        for (CorpseEntity c : corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20))) {
            check(c.getY() > groundY - 0.3, c.mobType() + " didn't sink into the ground (y=" + c.getY() + ")");
        }

        // save and load
        CompoundTag saved = zombieCorpse.saveWithoutId(new CompoundTag());
        CorpseEntity copy = com.harold.corpsedecay.ModRegistry.CORPSE.get().create(level);
        copy.load(saved);
        check(copy.settled() && copy.equipment(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE) && copy.arrows().size() == 1
                && copy.lootable(EquipmentSlot.CHEST) && copy.deathTime() == zombieCorpse.deathTime(), "corpse saves and loads");

        // looting
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(zombieCorpse.getX(), zombieCorpse.getY(), zombieCorpse.getZ() - 1.5);
        zombieCorpse.interact(player, InteractionHand.MAIN_HAND);
        check(!items(Items.DIAMOND_CHESTPLATE).isEmpty(), "right click takes the chestplate off");
        check(!items(Items.ARROW).isEmpty(), "right click pulls the arrow out");
        check(zombieCorpse.equipment(EquipmentSlot.CHEST).isEmpty(), "chestplate is gone from the body");
        check(zombieCorpse.equipment(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "helmet stays on");
        check(zombieCorpse.arrows().isEmpty(), "arrow is gone from the body");

        // shoving
        zombieCorpse.hurt(level.damageSources().playerAttack(player), 3F);
        check(!zombieCorpse.settled(), "hitting the body makes it ragdoll again");

        // arrows hitting a corpse stick in it
        Arrow arrow = new Arrow(level, zombieCorpse.getX(), zombieCorpse.getY() + 0.9, zombieCorpse.getZ() - 3);
        arrow.shoot(0, -0.28, 1, 2.0F, 0F);
        level.addFreshEntity(arrow);

        // rotting away
        CorpseEntity spider = byType(corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20)), "minecraft:spider");
        if (spider != null) spider.setDeathTime(level.getGameTime() - spider.decayTicks() - 5);
        CorpseEntity cow = byType(corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20)), "minecraft:cow");
        if (cow != null) cow.setDeathTime(level.getGameTime() - cow.decayTicks() - 5);
        CorpseEntity villager = byType(corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20)), "minecraft:villager");
        long skeletonEnd = (long) (com.harold.corpsedecay.Config.SKELETON_MINUTES.get() * 1200);
        if (villager != null) villager.setDeathTime(level.getGameTime() - villager.decayTicks() - skeletonEnd - 5);
        later(server, 45, () -> {
            List<CorpseEntity> left = corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20));
            check(byType(left, "minecraft:spider") == null, "a spider's husk fades away completely");
            CorpseEntity cowNow = byType(left, "minecraft:cow");
            check(cowNow != null && cowNow.decay(0) >= 1F, "a cow skeleton stays once the flesh has gone");
            check(byType(left, "minecraft:villager") == null, "an old skeleton crumbles away");
            check(zombieCorpse.arrows().size() == 1, "an arrow shot into a corpse sticks in it");
            wallTest(server);
        });
    }

    /** A mob killed while pushed against a wall must still end up lying down, not propped up. */
    private static void wallTest(MinecraftServer server) {
        for (int y = 0; y < 4; y++) {
            for (int x = 18; x <= 22; x++) level.setBlockAndUpdate(new BlockPos(x, groundY + y, 21), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        Mob victim = spawn(EntityType.ZOMBIE, 20, 20);
        victim.moveTo(20.5, groundY, 20.65, 0, 0);
        Mob attacker = spawn(EntityType.ZOMBIE, 20, 18);
        victim.hurt(level.damageSources().mobAttack(attacker), 1000F);
        attacker.discard();
        later(server, 120, () -> {
            CorpseEntity c = byType(corpses(new AABB(17, groundY - 2, 17, 24, groundY + 4, 24)), "minecraft:zombie");
            check(c != null, "zombie by the wall leaves a corpse");
            if (c != null) {
                Vector3f up = c.orientation().transform(new Vector3f(0, 1, 0));
                check(up.y < 0.6F, "body killed against a wall still ends up lying down (up y=" + up.y + ")");
            }
            fireTest(server);
        });
    }

    /** Burning to death leaves a charred body; setting a body alight chars it too. */
    private static void fireTest(MinecraftServer server) {
        Mob cow = spawn(EntityType.COW, 26, 26);
        cow.setSecondsOnFire(5);
        cow.hurt(level.damageSources().onFire(), 1000F);
        CorpseEntity plain = zombieCorpse;
        boolean wasBurnt = plain != null && plain.burnt();
        if (plain != null) {
            plain.setDeathTime(level.getGameTime());
            plain.hurt(level.damageSources().inFire(), 1F);
        }
        later(server, 3, () -> {
            CorpseEntity c = byType(corpses(new AABB(23, groundY - 2, 23, 30, groundY + 4, 30)), "minecraft:cow");
            check(c != null && c.burnt(), "a cow that burns to death leaves a charred body");
            check(c != null && c.decay(0) >= 0.74F && c.decay(0) < 0.9F, "a charred body starts just short of a skeleton (" + (c == null ? -1 : c.decay(0)) + ")");
            check(c != null && c.isOnFire(), "a charred body keeps burning for a moment");
            check(!wasBurnt && plain != null && plain.burnt() && plain.decay(0) >= 0.74F, "setting an old body alight chars it");
            limitTest(server);
        });
    }

    private static void limitTest(MinecraftServer server) {
        int max = com.harold.corpsedecay.Config.MAX_CORPSES.get();
        for (int i = 0; i < max + 8; i++) {
            Mob chicken = spawn(EntityType.CHICKEN, 30 + (i % 10) * 2, 30 + (i / 10) * 2);
            chicken.hurt(level.damageSources().generic(), 1000F);
        }
        later(server, 3, () -> {
            int count = corpses(new AABB(-100, groundY - 10, -100, 100, groundY + 20, 100)).size();
            check(count <= max, "no more than " + max + " corpses at once (" + count + ")");
            for (CorpseEntity c : corpses(new AABB(-100, groundY - 10, -100, 100, groundY + 20, 100))) c.discard();
            finish(server);
        });
    }

    private static final List<Object[]> SCHEDULED = new ArrayList<>();

    private static void later(MinecraftServer server, int ticks, Runnable r) {
        final int at = tick + ticks;
        SCHEDULED.add(new Object[] {at, r});
    }

    @SubscribeEvent
    public static void onTickScheduled(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (Object[] s : new ArrayList<>(SCHEDULED)) {
            if ((int) s[0] <= tick) {
                SCHEDULED.remove(s);
                try {
                    ((Runnable) s[1]).run();
                } catch (Throwable t) {
                    LOG.error("SELFTEST crashed", t);
                    failures++;
                    finish(ServerLifecycleHooks.getCurrentServer());
                }
            }
        }
    }

    private static void finish(MinecraftServer server) {
        if (done) return;
        done = true;
        LOG.info("SELFTEST {} passed, {} failed", passes, failures);
        LOG.info(failures == 0 ? "SELFTEST PASS" : "SELFTEST FAILED");
        server.halt(false);
    }

    private static List<CorpseEntity> corpses(AABB box) {
        return level.getEntitiesOfClass(CorpseEntity.class, box, c -> !c.isRemoved());
    }

    private static CorpseEntity nearest(List<CorpseEntity> list, double x, double z) {
        CorpseEntity best = null;
        double bd = Double.MAX_VALUE;
        for (CorpseEntity c : list) {
            double d = (c.getX() - x) * (c.getX() - x) + (c.getZ() - z) * (c.getZ() - z);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static CorpseEntity byType(List<CorpseEntity> list, String type) {
        for (CorpseEntity c : list) if (c.mobType().equals(type)) return c;
        return null;
    }

    private static CorpseKind kindOf(List<CorpseEntity> list, String type) {
        CorpseEntity c = byType(list, type);
        return c == null ? null : c.kind();
    }

    private static List<ItemEntity> items(Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20),
                i -> i.getItem().is(item));
    }

    @SuppressWarnings("unused")
    private static boolean alive(LivingEntity e) {
        return e != null && e.isAlive();
    }
}

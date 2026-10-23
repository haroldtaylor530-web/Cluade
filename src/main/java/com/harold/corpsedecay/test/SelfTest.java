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

        // arrows hitting a corpse stick in it (aimed at the golem's body, which is lying still)
        CorpseEntity golem = byType(corpses(new AABB(-20, groundY - 5, -20, 20, groundY + 10, 20)), "minecraft:iron_golem");
        if (golem != null) {
            net.minecraft.world.phys.Vec3 target = golem.getBoundingBox().getCenter();
            net.minecraft.world.phys.Vec3 from = target.add(0, 1.2, -3);
            Arrow arrow = new Arrow(level, from.x, from.y, from.z);
            net.minecraft.world.phys.Vec3 dir = target.subtract(from).normalize();
            arrow.shoot(dir.x, dir.y, dir.z, 2.5F, 0F);
            level.addFreshEntity(arrow);
        }

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
            CorpseEntity golemNow = byType(left, "minecraft:iron_golem");
            check(golemNow != null && golemNow.arrows().size() == 1, "an arrow shot into a corpse sticks in it");
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
            causeTest(server);
        });
    }

    /** Each way of dying leaves its own kind of remains. */
    private static void causeTest(MinecraftServer server) {
        var ds = level.damageSources();
        Object[][] cases = {
                {ds.drown(), com.harold.corpsedecay.DeathCause.DROWNED},
                {ds.freeze(), com.harold.corpsedecay.DeathCause.FROZEN},
                {ds.explosion(null, null), com.harold.corpsedecay.DeathCause.EXPLODED},
                {ds.wither(), com.harold.corpsedecay.DeathCause.WITHERED},
                {ds.fall(), com.harold.corpsedecay.DeathCause.FELL},
                {ds.anvil(null), com.harold.corpsedecay.DeathCause.CRUSHED},
        };
        for (int i = 0; i < cases.length; i++) {
            Mob cow = spawn(EntityType.COW, 40 + i * 4, 40);
            cow.hurt((net.minecraft.world.damagesource.DamageSource) cases[i][0], 1000F);
        }
        later(server, 3, () -> {
            int base = (int) Math.max(20, com.harold.corpsedecay.Config.MINUTES_TO_SKELETON.get() * 1200.0);
            for (int i = 0; i < cases.length; i++) {
                com.harold.corpsedecay.DeathCause want = (com.harold.corpsedecay.DeathCause) cases[i][1];
                CorpseEntity c = nearest(corpses(new AABB(37 + i * 4, groundY - 3, 37, 43 + i * 4, groundY + 5, 43)), 40.5 + i * 4, 40.5);
                check(c != null && c.cause() == want, "a cow killed by " + want + " leaves " + want + " remains (" + (c == null ? "none" : c.cause()) + ")");
                if (c == null) continue;
                if (want == com.harold.corpsedecay.DeathCause.FROZEN) check(c.decayTicks() == base * 4, "frozen bodies rot four times slower");
                if (want == com.harold.corpsedecay.DeathCause.WITHERED) check(c.decay(0) >= 0.54F, "withered bodies start as a dried husk");
                if (want == com.harold.corpsedecay.DeathCause.CRUSHED) check(c.squash() < 0.5F, "crushed bodies are flattened");
            }
            later(server, 100, () -> {
                CorpseEntity crushed = nearest(corpses(new AABB(57, groundY - 3, 37, 63, groundY + 5, 43)), 60.5, 40.5);
                if (crushed != null) {
                    Vector3f up = crushed.orientation().transform(new Vector3f(0, 1, 0));
                    check(up.y > 0.9F && crushed.getY() < groundY + 0.5, "a crushed body stays squashed flat where it stood (y=" + crushed.getY() + ")");
                }
                fossilTest(server);
            });
        });
    }

    /** Remains: placed like world generation places them, they last, can be looted and break into bones. */
    private static void fossilTest(MinecraftServer server) {
        var placed = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        for (String f : new String[] {"cave_remains", "surface_remains", "nether_remains", "end_remains", "fossil_bed",
                "ammonite_fossil", "trilobite_fossil", "fish_fossil", "fern_fossil"}) {
            check(placed.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", f)), f + " placement is loaded");
        }
        var mods = server.registryAccess().registryOrThrow(net.minecraftforge.registries.ForgeRegistries.Keys.BIOME_MODIFIERS);
        for (String m : new String[] {"cave_remains", "surface_remains", "nether_remains", "end_remains", "fossil_beds", "small_fossils"}) {
            check(mods.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", m)), m + " is added to biomes");
        }
        check(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getTag(CorpseKind.REMAINS_MOBS)
                .map(t -> t.size() > 10).orElse(false), "there's a list of mobs whose remains turn up");
        net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(5);
        EntityType<?>[] mobs = {EntityType.COW, EntityType.HORSE, EntityType.SKELETON, EntityType.SNIFFER};
        for (int i = 0; i < mobs.length; i++) {
            BlockPos floor = new BlockPos(40 + i * 5, groundY - 1, 50);
            CorpseEntity r = com.harold.corpsedecay.world.RemainsFeature.placeBody(level, level, floor, mobs[i], rand, i, i == 3 ? 0.4 : 0.1);
            check(r != null, "remains " + i + " made");
            if (r == null) continue;
            if (mobs[i] == EntityType.SKELETON) {
                r.giveGear(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.IRON_SWORD));
                r.addOldArrow(rand);
                r.addGrowth(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM, 0.5F, rand);
            }
            level.addFreshEntity(r);
        }
        // whole finds, the way world generation lays them out
        int finds = 0;
        for (int i = 0; i < 3; i++) {
            BlockPos floor = new BlockPos(40 + i * 6, groundY - 1, 58);
            if (com.harold.corpsedecay.world.RemainsFeature.placeRemains(level, level, floor, rand,
                    com.harold.corpsedecay.world.RemainsFeature.Where.SURFACE) != null) finds++;
        }
        check(finds == 3, "whole finds laid out (" + finds + ")");
        final int extra = finds;
        fossilBlockTest(server);
        later(server, 60, () -> {
            List<CorpseEntity> remains = corpses(new AABB(37, groundY - 4, 47, 62, groundY + 6, 53));
            check(remains.size() == 4, "remains stay put (" + remains.size() + ")");
            boolean all = true;
            for (CorpseEntity f : remains) all &= f.kind() == CorpseKind.REMAINS && f.settled();
            check(all, "remains lie still");
            CorpseEntity sniffer = byType(remains, "minecraft:sniffer");
            check(sniffer != null && sniffer.getY() < groundY + sniffer.getBoundingBox().getYsize() / 2 - 0.05,
                    "remains can lie half buried");
            CorpseEntity adventurer = byType(remains, "minecraft:skeleton");
            check(adventurer != null && adventurer.growths().size() == 1 && adventurer.arrows().size() == 1, "remains carry growths and old arrows");
            if (adventurer != null) {
                FakePlayer looter = FakePlayerFactory.getMinecraft(level);
                looter.moveTo(adventurer.getX(), adventurer.getY(), adventurer.getZ() - 1.5);
                adventurer.interact(looter, net.minecraft.world.InteractionHand.MAIN_HAND);
                check(!level.getEntitiesOfClass(ItemEntity.class, adventurer.getBoundingBox().inflate(3),
                        it -> it.getItem().is(Items.IRON_SWORD)).isEmpty(), "an adventurer's sword can be taken off its remains");
            }
            CorpseEntity f = byType(remains, "minecraft:cow");
            if (f != null) {
                FakePlayer p = FakePlayerFactory.getMinecraft(level);
                p.moveTo(f.getX(), f.getY(), f.getZ() - 1.5);
                p.setShiftKeyDown(true);
                f.hurt(level.damageSources().playerAttack(p), 1F);
                p.setShiftKeyDown(false);
                check(f.isRemoved() && !level.getEntitiesOfClass(ItemEntity.class,
                        f.getBoundingBox().inflate(3), it -> it.getItem().is(Items.BONE)).isEmpty(), "breaking remains gives bones");
            }
            mummyAndWhaleTest(server, () -> {
                remainsLeft = 3 + extra + diggers + extraRemains;
                limitTest(server);
            });
        });
    }

    /**
     * Bodies left in a swamp or the frozen north mummify; old bog bodies and frozen mummies; whale
     * skeletons on the sea floor and in the desert.
     */
    private static void mummyAndWhaleTest(MinecraftServer server, Runnable then) {
        var placed = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        var mods = server.registryAccess().registryOrThrow(net.minecraftforge.registries.ForgeRegistries.Keys.BIOME_MODIFIERS);
        for (String f : new String[] {"bog_body", "ice_mummy", "whale_fall", "desert_whales"}) {
            check(placed.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", f)), f + " placement is loaded");
        }
        for (String m : new String[] {"bog_bodies", "ice_mummies", "whale_falls", "desert_whales"}) {
            check(mods.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", m)), m + " is added to biomes");
        }
        // a strip of swamp and a strip of snowy plains, and animals dying in each
        var src = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, "fillbiome 8 " + (groundY - 4) + " -48 19 " + (groundY + 6) + " -38 minecraft:swamp");
        server.getCommands().performPrefixedCommand(src, "fillbiome 24 " + (groundY - 4) + " -48 35 " + (groundY + 6) + " -38 minecraft:snowy_plains");
        check(level.getBiome(new BlockPos(12, groundY, -44)).is(net.minecraft.world.level.biome.Biomes.SWAMP), "the test strip is swamp now");
        for (int cx = -1; cx <= 2; cx++) level.setChunkForced(cx, -3, true); // keep the strip ticking
        spawn(EntityType.COW, 11, -44).hurt(level.damageSources().generic(), 1000F);
        Mob burnt = spawn(EntityType.COW, 16, -44);
        burnt.setSecondsOnFire(5);
        burnt.hurt(level.damageSources().onFire(), 1000F);
        spawn(EntityType.VILLAGER, 27, -44).hurt(level.damageSources().generic(), 1000F);
        spawn(EntityType.SKELETON, 32, -44).hurt(level.damageSources().generic(), 1000F);
        spawn(EntityType.COW, 0, -44).hurt(level.damageSources().generic(), 1000F);
        // an old bog body and an old frozen hunter in a lump of ice
        net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(77);
        CorpseEntity bog = com.harold.corpsedecay.world.MummyFeature.bogBody(level, new BlockPos(38, groundY - 1, -24), rand);
        check(bog != null && bog.mummy() == CorpseEntity.BOG && level.getBlockState(new BlockPos(38, groundY - 1, -24)).is(net.minecraft.world.level.block.Blocks.MUD),
                "a bog body lies in the mud");
        CorpseEntity ice = com.harold.corpsedecay.world.MummyFeature.iceMummy(level, new BlockPos(44, groundY - 1, -30), rand, true);
        int iceBlocks = 0;
        if (ice != null) {
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(ice.position()).offset(-4, -3, -4), BlockPos.containing(ice.position()).offset(4, 3, 4))) {
                if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.ICE)) iceBlocks++;
            }
        }
        check(ice != null && ice.mummy() == CorpseEntity.ICE && iceBlocks >= 4, "a frozen mummy is locked in ice (" + iceBlocks + " ice)");
        if (ice != null) extraRemains++;
        if (bog != null) {
            FakePlayer p = FakePlayerFactory.getMinecraft(level);
            p.moveTo(bog.getX(), bog.getY(), bog.getZ() - 1.5);
            p.setShiftKeyDown(true);
            bog.hurt(level.damageSources().playerAttack(p), 1F);
            p.setShiftKeyDown(false);
            check(bog.isRemoved() && !level.getEntitiesOfClass(ItemEntity.class, bog.getBoundingBox().inflate(3),
                    it -> it.getItem().is(Items.LEATHER)).isEmpty(), "breaking up a mummy gives its dried skin as leather");
        }
        // whales: one on the sea floor (built in the air here), one turned to stone in the sand
        int bones = com.harold.corpsedecay.world.WhaleFeature.seaWhale(level, new BlockPos(25, groundY + 40, -30), net.minecraft.util.RandomSource.create(5),
                com.harold.corpsedecay.world.WhaleFeature.Kind.BLUE, net.minecraft.core.Direction.EAST);
        int ribs = 0;
        for (int x = 10; x <= 40; x++) {
            for (int z : new int[] {-33, -32, -28, -27}) {
                if (level.getBlockState(new BlockPos(x, groundY + 42, z)).is(net.minecraft.world.level.block.Blocks.BONE_BLOCK)
                        || level.getBlockState(new BlockPos(x, groundY + 43, z)).is(net.minecraft.world.level.block.Blocks.BONE_BLOCK)) ribs++;
            }
        }
        check(bones > 80 && ribs > 6, "a whale skeleton, its spine held up on arching ribs (" + bones + " bones, " + ribs + " rib tops)");
        int sperm = com.harold.corpsedecay.world.WhaleFeature.seaWhale(level, new BlockPos(25, groundY + 50, -30), net.minecraft.util.RandomSource.create(6),
                com.harold.corpsedecay.world.WhaleFeature.Kind.SPERM, net.minecraft.core.Direction.EAST);
        check(sperm > 60, "a sperm whale skeleton (" + sperm + " bones)");
        for (BlockPos p : BlockPos.betweenClosed(6, groundY + 22, -48, 44, groundY + 24, -12)) {
            level.setBlock(p, (p.getY() == groundY + 24 ? net.minecraft.world.level.block.Blocks.SAND : net.minecraft.world.level.block.Blocks.SANDSTONE).defaultBlockState(), 2);
        }
        int stone = com.harold.corpsedecay.world.WhaleFeature.desertWhale(level, new BlockPos(25, groundY + 24, -30), net.minecraft.util.RandomSource.create(7),
                net.minecraft.core.Direction.SOUTH);
        int fossilBones = 0;
        for (BlockPos p : BlockPos.betweenClosed(6, groundY + 24, -48, 44, groundY + 25, -12)) {
            if (level.getBlockState(p).is(com.harold.corpsedecay.block.FossilBlocks.FOSSIL_BONE.get())) fossilBones++;
        }
        check(stone > 20 && fossilBones > 15, "a stone whale lies half buried in the sand (" + fossilBones + " bones showing)");
        int cells = 0;
        for (int cx = 0; cx < 300; cx++) {
            for (int cz = 0; cz < 300; cz++) {
                if (com.harold.corpsedecay.world.WhaleFeature.graveyard(12345L, cx * com.harold.corpsedecay.world.WhaleFeature.CELL,
                        cz * com.harold.corpsedecay.world.WhaleFeature.CELL)) cells++;
            }
        }
        check(cells > 90000 * 0.02 && cells < 90000 * 0.04, "whale gatherings are rare (" + cells + " of 90000 stretches of sea)");
        later(server, 3, () -> {
            List<CorpseEntity> strip = corpses(new AABB(-4, groundY - 4, -48, 40, groundY + 6, -38));
            CorpseEntity bogCow = nearest(strip, 11.5, -43.5), burntCow = nearest(strip, 16.5, -43.5);
            CorpseEntity iceMan = byType(strip, "minecraft:villager"), skel = byType(strip, "minecraft:skeleton");
            CorpseEntity plainCow = nearest(strip, 0.5, -43.5);
            check(bogCow != null && bogCow.mummy() == CorpseEntity.BOG, "a cow that dies in a swamp turns into a bog body");
            check(burntCow != null && burntCow.burnt() && burntCow.mummy() == 0, "but not if it burnt");
            check(iceMan != null && iceMan.mummy() == CorpseEntity.ICE, "a villager that dies in the snow freeze-dries");
            check(skel != null && skel.mummy() == 0, "a skeleton has no skin to mummify");
            check(plainCow != null && plainCow.mummy() == 0, "a cow that dies on the plains rots as usual");
            long skeletonEnd = (long) (com.harold.corpsedecay.Config.SKELETON_MINUTES.get() * 1200);
            if (bogCow != null) bogCow.setDeathTime(level.getGameTime() - bogCow.decayTicks() - skeletonEnd - 5);
            if (plainCow != null) plainCow.setDeathTime(level.getGameTime() - plainCow.decayTicks() - skeletonEnd - 5);
            later(server, 45, () -> {
                List<CorpseEntity> left = corpses(new AABB(-4, groundY - 4, -48, 40, groundY + 6, -38));
                check(bogCow != null && !bogCow.isRemoved() && bogCow.decay(0) >= 1F, "a mummy keeps long after a skeleton would have crumbled");
                check(plainCow != null && plainCow.isRemoved(), "while an ordinary skeleton of the same age has crumbled");
                for (CorpseEntity c : left) if (c.kind() != CorpseKind.REMAINS) c.discard();
                then.run();
            });
        });
    }

    private static int remainsLeft;
    private static int diggers;
    private static int extraRemains;

    /** Fossil blocks: every kind of skeleton set into rock, bone beds, trees, nests, trails, a dig site. */
    private static void fossilBlockTest(MinecraftServer server) {
        var placed = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        for (String f : new String[] {"footprint_trail", "cave_footprint_trail", "petrified_tree", "fossil_nest",
                "dig_site", "amber", "amber_mosquito", "scallop_fossil", "shark_tooth_fossil", "crinoid_fossil"}) {
            check(placed.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", f)), f + " placement is loaded");
        }
        var mods = server.registryAccess().registryOrThrow(net.minecraftforge.registries.ForgeRegistries.Keys.BIOME_MODIFIERS);
        for (String m : new String[] {"footprint_trails", "petrified_trees", "fossil_nests", "dig_sites", "amber"}) {
            check(mods.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", m)), m + " is added to biomes");
        }
        int y0 = groundY + 12;
        // a block of rock, deepslate at one end, to set every kind of skeleton into
        for (BlockPos p : BlockPos.betweenClosed(-44, y0, -44, -2, y0 + 9, -4)) {
            var rock = p.getX() < -30 ? net.minecraft.world.level.block.Blocks.DEEPSLATE : net.minecraft.world.level.block.Blocks.STONE;
            level.setBlock(p, rock.defaultBlockState(), 2);
        }
        int z = -6, allOk = 0, kinds = 0;
        for (String[] sk : com.harold.corpsedecay.block.FossilShapes.SKELETONS) {
            String[] rows = com.harold.corpsedecay.world.FossilBedFeature.named(sk[0]);
            int want = 0;
            for (String r : rows) want += r.substring(1).replace(" ", "").length();
            int got = com.harold.corpsedecay.world.FossilBedFeature.placeSkeleton(level, new BlockPos(-22, y0 + 5, z),
                    net.minecraft.util.RandomSource.create(1), rows, 0F, 0.75F, net.minecraft.core.Direction.EAST);
            kinds++;
            if (got == want) allOk++;
            else LOG.warn("SELFTEST skeleton {} placed {}/{}", sk[0], got, want);
            z -= 2;
        }
        check(allOk == kinds && kinds >= 15, "every kind of skeleton goes into the rock whole (" + allOk + "/" + kinds + ")");
        int stone = 0, deep = 0, skulls = 0;
        boolean facing = true;
        for (BlockPos p : BlockPos.betweenClosed(-44, y0, -44, -2, y0 + 9, -4)) {
            var st = level.getBlockState(p);
            var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock());
            if (key == null || !key.getNamespace().equals("corpsedecay")) continue;
            if (key.getPath().startsWith("stone_")) stone++;
            if (key.getPath().startsWith("deepslate_")) deep++;
            if (key.getPath().endsWith("skull_fossil")) {
                skulls++;
                facing &= st.getValue(com.harold.corpsedecay.block.FossilBlocks.Pointing.FACING) == net.minecraft.core.Direction.EAST;
            }
        }
        check(stone > 0 && deep > 0, "fossils take on the rock they're in (" + stone + " stone, " + deep + " deepslate)");
        check(skulls == kinds && facing, "one skull to each skeleton, pointing the way the head does (" + skulls + ")");
        // varied lengths still make whole animals
        net.minecraft.util.RandomSource vr = net.minecraft.util.RandomSource.create(7);
        boolean varied = false, sound = true;
        for (int i = 0; i < 200; i++) {
            String[] base = com.harold.corpsedecay.world.FossilBedFeature.pick(vr);
            String[] v = com.harold.corpsedecay.world.FossilBedFeature.vary(base, vr);
            if (v[0].length() != base[0].length()) varied = true;
            int sk = 0;
            for (String r : v) for (char ch : r.substring(1).toCharArray()) if ("KHCBG".indexOf(ch) >= 0) sk++;
            sound &= sk == 1;
        }
        check(varied && sound, "skeletons come in different lengths and keep their heads");
        int notInRock = com.harold.corpsedecay.world.FossilBedFeature.placeSkeleton(level, new BlockPos(0, groundY + 40, 0),
                net.minecraft.util.RandomSource.create(1), com.harold.corpsedecay.world.FossilBedFeature.named("tyrant"), 0F, 0.75F);
        check(notInRock == 0, "no fossil skeleton is left hanging in the air");
        // a bone bed, a petrified tree and a nest in a fresh block of stone
        for (BlockPos p : BlockPos.betweenClosed(-44, y0 + 12, -44, -2, y0 + 22, -4)) level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        int bed = com.harold.corpsedecay.world.FossilBedFeature.boneBed(level, new BlockPos(-35, y0 + 17, -12), net.minecraft.util.RandomSource.create(3));
        check(bed > 5, "a bone bed is a jumble of loose bones (" + bed + ")");
        int tree = com.harold.corpsedecay.world.PetrifiedTreeFeature.grow(level, new BlockPos(-25, y0 + 17, -12), net.minecraft.util.RandomSource.create(4), false);
        check(tree >= 4 && level.getBlockState(new BlockPos(-25, y0 + 17, -12)).is(com.harold.corpsedecay.block.FossilBlocks.PETRIFIED_LOG.get()),
                "a petrified trunk lies in the rock (" + tree + ")");
        int eggs = com.harold.corpsedecay.world.FossilNestFeature.nest(level, new BlockPos(-12, y0 + 15, -25), net.minecraft.util.RandomSource.create(5));
        int eggBlocks = 0;
        for (BlockPos p : BlockPos.betweenClosed(-13, y0 + 16, -26, -11, y0 + 16, -24)) {
            if (level.getBlockState(p).is(com.harold.corpsedecay.block.FossilBlocks.FOSSIL_EGG.get())) eggBlocks++;
        }
        check(eggs > 0 && eggBlocks > 0, "a buried nest holds stone eggs (" + eggs + " eggs)");
        // footprints across a bare rock floor
        for (BlockPos p : BlockPos.betweenClosed(56, groundY - 1, -30, 80, groundY - 1, -20)) level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        int prints = com.harold.corpsedecay.world.FootprintTrailFeature.trail(level, new BlockPos(58, groundY - 1, -25), net.minecraft.util.RandomSource.create(6),
                net.minecraft.core.Direction.EAST, 8);
        check(prints >= 6, "a trail of footprints crosses the rock (" + prints + ")");
        // a dig site
        BlockPos digAt = new BlockPos(30, groundY, -40);
        boolean dug = com.harold.corpsedecay.world.DigSiteFeature.dig(level, digAt, net.minecraft.util.RandomSource.create(8), net.minecraft.core.Direction.EAST, false);
        int chests = 0, suspicious = 0, ladders = 0, wallBones = 0;
        for (BlockPos p : BlockPos.betweenClosed(20, groundY - 8, -44, 40, groundY + 3, -32)) {
            var st = level.getBlockState(p);
            if (st.is(net.minecraft.world.level.block.Blocks.CHEST)) chests++;
            if (st.is(net.minecraft.world.level.block.Blocks.SUSPICIOUS_GRAVEL) || st.is(net.minecraft.world.level.block.Blocks.SUSPICIOUS_SAND)) suspicious++;
            if (st.is(net.minecraft.world.level.block.Blocks.LADDER)) ladders++;
            var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock());
            if (key != null && key.getNamespace().equals("corpsedecay") && key.getPath().endsWith("_fossil")) wallBones++;
        }
        diggers = corpses(new AABB(20, groundY - 8, -44, 40, groundY + 3, -32)).size();
        check(dug && chests == 1 && ladders > 0 && wallBones > 3, "a dig site: pit, ladder, chest and a skeleton in the wall ("
                + chests + " chest, " + ladders + " ladder, " + wallBones + " bones, " + suspicious + " suspicious)");
        // mining
        var block = com.harold.corpsedecay.block.FossilBlocks.get(com.harold.corpsedecay.block.FossilBlocks.Host.TERRACOTTA, "shark_tooth");
        BlockPos at = new BlockPos(-10, y0 + 30, -5);
        level.setBlock(at, block.defaultBlockState(), 2);
        var drops = net.minecraft.world.level.block.Block.getDrops(block.defaultBlockState(), level, at, null, null,
                new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
        check(drops.size() == 1 && drops.get(0).is(block.asItem()), "mining a fossil block gives the fossil (" + drops + ")");
        check(block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), "fossil blocks are mined with a pickaxe");
        var three = com.harold.corpsedecay.block.FossilBlocks.FOSSIL_EGG.get().defaultBlockState().setValue(com.harold.corpsedecay.block.FossilEggBlock.EGGS, 3);
        var eggDrops = net.minecraft.world.level.block.Block.getDrops(three, level, at, null, null, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
        check(eggDrops.size() == 1 && eggDrops.get(0).getCount() == 3, "a clutch of three eggs gives three eggs (" + eggDrops + ")");
        check(com.harold.corpsedecay.block.FossilBlocks.pieces().size() >= 30, "lots of kinds of fossil (" + com.harold.corpsedecay.block.FossilBlocks.pieces().size() + ")");
        cambrianAndNeolithicTest(server, y0);
    }

    /** Every rock formation, laid into a block of stone up in the air, and a tar pit. */
    private static void formationTest(MinecraftServer server) {
        var placed = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        var mods = server.registryAccess().registryOrThrow(net.minecraftforge.registries.ForgeRegistries.Keys.BIOME_MODIFIERS);
        var all = com.harold.corpsedecay.world.FormationFeature.Formation.values();
        int good = 0;
        for (int i = 0; i < all.length; i++) {
            var f = all[i];
            check(placed.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", f.id()))
                    && mods.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", "formation_" + f.id())), f.id() + " generates in its biomes");
            int cx = -32 + (i % 3) * 30, cz = -32 + ((i / 3) % 3) * 30, cy = groundY + 70 + (i / 9) * 22;
            for (BlockPos p : BlockPos.betweenClosed(cx - 12, cy - 9, cz - 12, cx + 12, cy + 9, cz + 12)) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            var r = com.harold.corpsedecay.world.FormationFeature.lay(level, new BlockPos(cx, cy, cz), net.minecraft.util.RandomSource.create(20 + i), f, 10, 5, 10);
            int stoneLeft = 0;
            for (BlockPos p : BlockPos.betweenClosed(cx - 3, cy - 2, cz - 3, cx + 3, cy + 2, cz + 3)) {
                if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)) stoneLeft++;
            }
            boolean ok = r.rock() > 1500 && r.fossils() >= 3 && stoneLeft < 10;
            LOG.info("SELFTEST formation {}: {} rock, {} fossils, {} stone left in the middle", f.id(), r.rock(), r.fossils(), stoneLeft);
            if (ok) good++;
        }
        check(good == all.length, "every formation lays down its layers and fossils (" + good + "/" + all.length + ")");
        check(com.harold.corpsedecay.block.FossilBlocks.has(com.harold.corpsedecay.block.FossilBlocks.Host.SHALE, "opabinia")
                && !com.harold.corpsedecay.block.FossilBlocks.has(com.harold.corpsedecay.block.FossilBlocks.Host.CHALK, "opabinia")
                && com.harold.corpsedecay.block.FossilBlocks.has(com.harold.corpsedecay.block.FossilBlocks.Host.PERMAFROST, "tusk"),
                "each rock holds the fossils of its time");
        var flint = net.minecraft.world.level.block.Block.getDrops(com.harold.corpsedecay.block.FossilBlocks.CHALK_FLINT.get().defaultBlockState(),
                level, new BlockPos(0, groundY + 100, 0), null, null, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
        check(!flint.isEmpty() && flint.get(0).is(Items.FLINT), "chalk gives up its flint (" + flint + ")");
        var coal = net.minecraft.world.level.block.Block.getDrops(com.harold.corpsedecay.block.FossilBlocks.COAL_SEAM.get().defaultBlockState(),
                level, new BlockPos(0, groundY + 100, 0), null, null, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
        check(!coal.isEmpty() && coal.get(0).is(Items.COAL), "coal seams give coal (" + coal + ")");
        check(net.minecraftforge.common.ForgeHooks.getBurnTime(new net.minecraft.world.item.ItemStack(com.harold.corpsedecay.block.FossilBlocks.LIGNITE.get()), null) > 0,
                "lignite burns");
        int animals = com.harold.corpsedecay.world.TarPitFeature.pit(level, new BlockPos(-20, groundY, 0), net.minecraft.util.RandomSource.create(30));
        check(animals > 0 && level.getBlockState(new BlockPos(-20, groundY - 1, 0)).is(com.harold.corpsedecay.block.FossilBlocks.TAR.get()),
                "a tar pit with animals caught in it (" + animals + ")");
        extraRemains += animals;
    }

    /** The Cambrian beds deep down, and the Stone Age sites. */
    private static void cambrianAndNeolithicTest(MinecraftServer server, int y0) {
        var placed = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        for (String f : new String[] {"cambrian_bed", "opabinia_fossil", "hallucigenia_fossil", "painted_cave", "stone_circle", "dolmen", "barrow", "bone_hut"}) {
            check(placed.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", f)), f + " placement is loaded");
        }
        var mods = server.registryAccess().registryOrThrow(net.minecraftforge.registries.ForgeRegistries.Keys.BIOME_MODIFIERS);
        for (String m : new String[] {"cambrian_fossils", "painted_caves", "neolithic_sites", "bone_huts"}) {
            check(mods.containsKey(new net.minecraft.resources.ResourceLocation("corpsedecay", m)), m + " is added to biomes");
        }
        // a Cambrian bed, and an anomalocaris, in a fresh block of deepslate
        for (BlockPos p : BlockPos.betweenClosed(-44, y0 + 32, -44, -2, y0 + 38, -4)) level.setBlock(p, net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState(), 2);
        int bed = com.harold.corpsedecay.world.CambrianBedFeature.bed(level, new BlockPos(-30, y0 + 35, -20), net.minecraft.util.RandomSource.create(9));
        check(bed > 6, "a Cambrian bed is crowded with sea creatures (" + bed + ")");
        BlockPos head = new BlockPos(-10, y0 + 35, -30);
        int anomalo = com.harold.corpsedecay.world.CambrianBedFeature.anomalocaris(level, head, net.minecraft.util.RandomSource.create(2));
        var hs = level.getBlockState(head);
        boolean whole = anomalo == 2 && hs.getBlock() == com.harold.corpsedecay.block.FossilBlocks.get(com.harold.corpsedecay.block.FossilBlocks.Host.DEEPSLATE, "anomalocaris_head")
                && level.getBlockState(head.relative(hs.getValue(com.harold.corpsedecay.block.FossilBlocks.Pointing.FACING).getOpposite())).getBlock()
                == com.harold.corpsedecay.block.FossilBlocks.get(com.harold.corpsedecay.block.FossilBlocks.Host.DEEPSLATE, "anomalocaris_tail");
        check(whole, "an anomalocaris lies whole, its tail behind its head");
        // a painted cave: a hollow sealed in stone
        for (BlockPos p : BlockPos.betweenClosed(-44, y0 + 42, -44, -30, y0 + 50, -30)) level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(-40, y0 + 44, -40, -34, y0 + 47, -34)) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        int paintings = com.harold.corpsedecay.world.NeolithicFeature.paintedCave(level, new BlockPos(-37, y0 + 45, -37), net.minecraft.util.RandomSource.create(10));
        check(paintings >= 3, "a cave wall gets painted (" + paintings + ")");
        // the sites on the surface
        net.minecraft.util.RandomSource nr = net.minecraft.util.RandomSource.create(11);
        boolean circle = com.harold.corpsedecay.world.NeolithicFeature.stoneCircle(level, new BlockPos(-34, groundY, 36), nr, true);
        int standing = 0;
        for (BlockPos p : BlockPos.betweenClosed(-44, groundY, 26, -24, groundY + 4, 46)) {
            var st = level.getBlockState(p);
            if (st.is(net.minecraft.world.level.block.Blocks.STONE) || st.is(net.minecraft.world.level.block.Blocks.ANDESITE) || st.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)
                    || st.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE) || st.is(net.minecraft.world.level.block.Blocks.TUFF)) standing++;
        }
        check(circle && standing >= 12, "a ring of standing stones (" + standing + " stone blocks)");
        boolean barrow = com.harold.corpsedecay.world.NeolithicFeature.barrow(level, new BlockPos(-12, groundY, 40), nr, net.minecraft.core.Direction.EAST);
        int chests = 0, pots = 0, grass = 0;
        for (BlockPos p : BlockPos.betweenClosed(-20, groundY - 3, 34, -4, groundY + 5, 46)) {
            var st = level.getBlockState(p);
            if (st.is(net.minecraft.world.level.block.Blocks.CHEST)) chests++;
            if (st.is(net.minecraft.world.level.block.Blocks.DECORATED_POT)) pots++;
            if (st.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && p.getY() > groundY) grass++;
        }
        check(barrow && chests == 1 && pots == 2 && grass > 20, "a barrow: a grassy mound over a chamber with grave goods ("
                + chests + " chest, " + pots + " pots, " + grass + " grass)");
        boolean hut = com.harold.corpsedecay.world.NeolithicFeature.boneHut(level, new BlockPos(-12, groundY, 22), nr, net.minecraft.core.Direction.NORTH);
        int bones = 0;
        boolean hearth = level.getBlockState(new BlockPos(-12, groundY, 22)).is(net.minecraft.world.level.block.Blocks.CAMPFIRE);
        for (BlockPos p : BlockPos.betweenClosed(-16, groundY, 18, -8, groundY + 3, 26)) {
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.BONE_BLOCK)) bones++;
        }
        check(hut && hearth && bones > 15, "a mammoth-bone hut round a hearth (" + bones + " bones)");
        boolean dolmen = com.harold.corpsedecay.world.NeolithicFeature.dolmen(level, new BlockPos(-26, groundY, 14), nr);
        check(dolmen && !level.getBlockState(new BlockPos(-26, groundY + 2, 14)).isAir(), "a dolmen's capstone stands on its stones");
        for (CorpseEntity c : corpses(new AABB(-48, groundY - 10, -48, 0, groundY + 20, 48))) {
            if (c.kind() == CorpseKind.REMAINS) extraRemains++;
        }
        // things of the time
        check(com.harold.corpsedecay.block.FossilBlocks.FLINT_HAND_AXE.get() instanceof net.minecraft.world.item.AxeItem, "a flint hand axe is an axe");
        var recipes = server.getRecipeManager();
        for (String r : new String[] {"flint_hand_axe", "bone_flute", "red_dye_from_ochre"}) {
            check(recipes.byKey(new net.minecraft.resources.ResourceLocation("corpsedecay", r)).isPresent(), r + " can be crafted");
        }
        formationTest(server);
        check(com.harold.corpsedecay.block.FossilBlocks.pieces(com.harold.corpsedecay.block.FossilBlocks.CAMBRIAN).size() == 10
                && com.harold.corpsedecay.block.FossilBlocks.pieces(com.harold.corpsedecay.block.FossilBlocks.PAINTING).size() == 7,
                "ten Cambrian fossils and seven cave paintings");
    }

    private static void limitTest(MinecraftServer server) {
        int max = com.harold.corpsedecay.Config.MAX_CORPSES.get();
        for (int i = 0; i < max + 8; i++) {
            Mob chicken = spawn(EntityType.CHICKEN, 30 + (i % 10) * 2, 30 + (i / 10) * 2);
            chicken.hurt(level.damageSources().generic(), 1000F);
        }
        later(server, 3, () -> {
            int count = corpses(new AABB(-100, groundY - 10, -100, 100, groundY + 20, 100)).size();
            int fossilsLeft = 0;
            for (CorpseEntity c : corpses(new AABB(-100, groundY - 10, -100, 100, groundY + 20, 100))) {
                if (c.kind() == CorpseKind.REMAINS) fossilsLeft++;
            }
            check(count - fossilsLeft <= max, "no more than " + max + " corpses at once (" + (count - fossilsLeft) + ")");
            check(fossilsLeft == remainsLeft, "remains don't count towards the limit and never crumble (" + fossilsLeft + " left of " + remainsLeft + ")");
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

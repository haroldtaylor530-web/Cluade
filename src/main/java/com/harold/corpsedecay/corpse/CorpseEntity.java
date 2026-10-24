package com.harold.corpsedecay.corpse;

import java.util.function.Consumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.CorpseKind;
import com.harold.corpsedecay.DeathCause;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What's left of a mob after it dies. Server side it runs the ragdoll until the body comes to
 * rest, keeps the mob's gear and stuck arrows, and ages; the client draws the original mob's
 * model in the body's pose with a texture for its current stage of decay.
 */
public class CorpseEntity extends Entity {
    private static final EntityDataAccessor<String> MOB_TYPE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> RENDER_TAG = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> BODY_W = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BODY_H = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BODY_D = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Quaternionf> ORIENT = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Boolean> SETTLED = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> DEATH_TIME = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> BURN_TIME = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DECAY_TICKS = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> CAUSE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SQUASH = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> MATERIAL = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> LOOT_MASK = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> ARROWS = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> DECOR = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** limbs held still in a pose (part name -> x, y, z angles) for a body frozen where it stood */
    private static final EntityDataAccessor<CompoundTag> FIGURE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Byte> MUMMY = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BYTE);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<ItemStack>[] EQUIP = new EntityDataAccessor[6];

    static {
        for (int i = 0; i < 6; i++) {
            EQUIP[i] = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
        }
    }

    public static final int MAX_ARROWS = 16;
    private static final byte EVENT_CRUMBLE = 61;

    /** Set by the client setup; runs the client side of each corpse tick (limbs, flies). */
    public static Consumer<CorpseEntity> clientTick;
    /** Set by the client setup; called when the server says the corpse crumbled. */
    public static Consumer<CorpseEntity> clientCrumble;

    /** Client-only state (puppet, limb physics), owned by the client code. */
    public Object clientState;
    /** Bumped whenever the look of the corpse changes so the client rebuilds its puppet. */
    public int lookVersion;

    private Ragdoll ragdoll;
    private long skeletonTicks;
    /** extra lootable items stored in the mob's own data (saddles, horse armour, llama carpets): key -> stack */
    private CompoundTag extraLoot = new CompoundTag();
    private final boolean ready;
    private boolean checkedUpright;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.ready = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MOB_TYPE, "minecraft:pig");
        entityData.define(RENDER_TAG, new CompoundTag());
        entityData.define(FIGURE, new CompoundTag());
        entityData.define(BODY_W, 0.6F);
        entityData.define(BODY_H, 1.8F);
        entityData.define(BODY_D, 0.6F);
        entityData.define(ORIENT, new Quaternionf());
        entityData.define(SETTLED, false);
        entityData.define(DEATH_TIME, 0L);
        entityData.define(BURN_TIME, 0L);
        entityData.define(DECAY_TICKS, 24000);
        entityData.define(KIND, (byte) 0);
        entityData.define(CAUSE, (byte) 0);
        entityData.define(SQUASH, 1F);
        entityData.define(MATERIAL, (byte) 0);
        entityData.define(LOOT_MASK, 0);
        entityData.define(ARROWS, new CompoundTag());
        entityData.define(DECOR, new CompoundTag());
        entityData.define(MUMMY, (byte) 0);
        for (EntityDataAccessor<ItemStack> a : EQUIP) entityData.define(a, ItemStack.EMPTY);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == MOB_TYPE || key == RENDER_TAG) lookVersion++;
        if (key == BODY_W || key == BODY_H || key == BODY_D || key == ORIENT || key == SQUASH) {
            if (ready) setBoundingBox(makeBoundingBox());
        }
    }

    // ---- setup ---------------------------------------------------------------------------------

    /** Fills this corpse from a snapshot of the mob taken when it died. */
    public void setup(DeathSnapshot s) {
        entityData.set(MOB_TYPE, s.type);
        entityData.set(RENDER_TAG, s.renderTag);
        entityData.set(BODY_W, s.width);
        entityData.set(BODY_H, s.height);
        boolean upright = s.height > s.width * 1.9F;
        boolean flat = s.width >= s.height * 1.5F;
        // four-legged bodies are long and low, so they roll onto their side rather than stand on end
        float depth = upright || flat ? s.width : Math.max(s.width, s.height * 1.3F);
        entityData.set(BODY_D, depth);
        entityData.set(DEATH_TIME, level().getGameTime());
        entityData.set(DECAY_TICKS, (int) Math.max(20, Config.MINUTES_TO_SKELETON.get() * 1200.0));
        skeletonTicks = (long) (Config.SKELETON_MINUTES.get() * 1200.0);
        entityData.set(KIND, (byte) s.kind.ordinal());
        DeathCause cause = s.cause;
        entityData.set(CAUSE, (byte) cause.ordinal());
        if (cause == DeathCause.FROZEN) entityData.set(DECAY_TICKS, decayTicks() * 4); // frozen bodies keep
        if (cause == DeathCause.CRUSHED) entityData.set(SQUASH, 0.3F);
        entityData.set(MUMMY, (byte) s.mummy);
        if (s.mummy != 0) skeletonTicks *= 4; // a mummy keeps much longer than bare bones
        int mask = 0;
        for (int i = 0; i < 6; i++) {
            entityData.set(EQUIP[i], s.equipment[i]);
            if (s.lootable[i] && !s.equipment[i].isEmpty()) mask |= 1 << i;
        }
        entityData.set(LOOT_MASK, mask);
        CompoundTag arrows = new CompoundTag();
        arrows.put("List", s.arrows);
        entityData.set(ARROWS, arrows);
        extraLoot = s.extraLoot;
        if (s.name != null) setCustomName(s.name);

        Quaternionf q = new Quaternionf().rotationY(-s.bodyYaw * Mth.DEG_TO_RAD);
        Vec3 center = s.feet.add(0, s.height / 2.0, 0);
        double force = Config.RAGDOLL_FORCE.get();
        Vec3 vel = s.velocity.add(s.push.scale(force));
        if (!upright && !flat) {
            // animals drop where they stand rather than flying (sliding would trip them end over end)
            vel = new Vec3(vel.x * 0.3, Math.min(vel.y, 0.15), vel.z * 0.3);
        }
        if (vel.lengthSqr() > 1.0) vel = vel.normalize();
        if (cause == DeathCause.CRUSHED) {
            // flattened where it stood
            vel = Vec3.ZERO;
            center = s.feet.add(0, s.height * squash() / 2.0, 0);
        }
        ragdoll = makeRagdoll();
        ragdoll.place(center, q, vel, new Vector3f());

        // which way the body goes down: upright bodies topple the way they were hit,
        // four-legged ones roll onto their side
        double dx = s.push.x, dz = s.push.z;
        if (dx * dx + dz * dz < 1e-6) {
            double a = random.nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
        }
        if (!upright && !flat) {
            Vector3f right = q.transform(new Vector3f(1, 0, 0));
            double side = right.x * dx + right.z * dz;
            if (Math.abs(side) < 0.2) side = random.nextBoolean() ? 1 : -1;
            dx = right.x * Math.signum(side);
            dz = right.z * Math.signum(side);
        }
        // push the top over hard enough to beat gravity holding a wide body up, and only until it
        // has passed its tipping point (so square bodies like pigs don't roll right onto their back)
        double strength = Math.max(0.02 + 0.01 * Math.min(2.0, force), 1.35 * 0.08 * s.width / s.height);
        double stop = upright ? 0.55 : Math.cos(Math.atan(s.width / s.height) + 0.03);
        if (cause == DeathCause.CRUSHED) {
            // no toppling: it's been squashed flat
        } else if (flat) {
            ragdoll.collapse(dx, dz, 0.004, 10, 0, 1.0); // flat bodies (spiders) just slump
        } else {
            ragdoll.collapse(dx, dz, strength, upright ? 14 : 0, 60, stop);
        }

        setPos(center);
        entityData.set(ORIENT, q);
        entityData.set(SETTLED, false);
        lookVersion++;
        if (s.burning) burn();
        if (cause == DeathCause.WITHERED) {
            long target = (long) (WITHERED_DECAY * decayTicks());
            entityData.set(DEATH_TIME, level().getGameTime() - target);
        }
    }

    /** How far through decay a withered body starts: already a shrivelled husk. */
    public static final float WITHERED_DECAY = 0.55F;

    private Ragdoll makeRagdoll() {
        Ragdoll r = new Ragdoll(bodyWidth(), bodyHeight() * squash(), bodyDepth());
        r.buoyant = cause() == DeathCause.DROWNED;
        return r;
    }

    public DeathCause cause() {
        return DeathCause.byId(entityData.get(CAUSE));
    }

    /** 1 normally; less for a body squashed flat. */
    public float squash() {
        return entityData.get(SQUASH);
    }

    /** How old remains have weathered: see the constants below. */
    public int material() {
        return entityData.get(MATERIAL);
    }

    /**
     * old yellowed bone, sandy, grimy cave bone, sun-bleached, mossy, charred, ender-stained; and two
     * kinds of mummy, where the skin never rotted off: a dark leathery bog body, and a freeze-dried one
     */
    public static final int OLD = 0, SANDY = 1, DARK = 2, BLEACHED = 3, MOSSY = 4, CHARRED = 5, ENDER = 6, BOG_MUMMY = 7, ICE_MUMMY = 8;

    /** How a body mummifies instead of rotting: in a peat bog, or freeze-dried in the cold. */
    public static final int BOG = 1, ICE = 2;

    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> MUMMIFIES_IN_BOG = biomeTag("mummifies_in_bog");
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> MUMMIFIES_IN_ICE = biomeTag("mummifies_in_ice");

    private static net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomeTag(String name) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                new net.minecraft.resources.ResourceLocation(com.harold.corpsedecay.CorpseDecay.MODID, name));
    }

    /**
     * Whether a body of this kind, dying this way at {@code pos}, mummifies: bodies with flesh on
     * them turn into bog bodies in swamps and freeze-dry in frozen places, unless fire or a blast
     * got to them first.
     */
    public static int mummyFor(Level level, net.minecraft.core.BlockPos pos, CorpseKind kind, DeathCause cause) {
        if (!Config.MUMMIFY.get() || kind != CorpseKind.FLESH) return 0;
        if (cause == DeathCause.BURNT || cause == DeathCause.EXPLODED || cause == DeathCause.WITHERED) return 0;
        var biome = level.getBiome(pos);
        if (biome.is(MUMMIFIES_IN_BOG)) return BOG;
        if (biome.is(MUMMIFIES_IN_ICE)) return ICE;
        return 0;
    }

    /** {@link #BOG}, {@link #ICE} or 0: whether this body is (or is turning into) a mummy. */
    public int mummy() {
        if (kind() == CorpseKind.REMAINS) {
            int m = material();
            return m == BOG_MUMMY ? BOG : m == ICE_MUMMY ? ICE : 0;
        }
        return entityData.get(MUMMY);
    }

    /**
     * Remains: the bones of a mob that died long ago, lying on {@code ground} (the top of the
     * block it rests on), weathered as {@code material} and sunk {@code sink} (0..1) of its
     * thickness into the ground.
     */
    public static CorpseEntity remains(ServerLevel level, EntityType<?> mob, Vec3 ground, net.minecraft.util.RandomSource rand, int material, double sink) {
        CorpseEntity c = com.harold.corpsedecay.ModRegistry.CORPSE.get().create(level);
        if (c == null) return null;
        float w = mob.getWidth(), h = mob.getHeight();
        boolean upright = h > w * 1.9F;
        boolean flat = w >= h * 1.5F;
        c.entityData.set(MOB_TYPE, String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob)));
        c.entityData.set(BODY_W, w);
        c.entityData.set(BODY_H, h);
        c.entityData.set(BODY_D, upright || flat ? w : Math.max(w, h * 1.3F));
        c.entityData.set(KIND, (byte) CorpseKind.REMAINS.ordinal());
        c.entityData.set(MATERIAL, (byte) material);
        c.entityData.set(DECAY_TICKS, 20);
        c.entityData.set(DEATH_TIME, 0L);
        c.skeletonTicks = 0;
        float yaw = rand.nextFloat() * Mth.TWO_PI;
        float side = rand.nextBoolean() ? Mth.HALF_PI : -Mth.HALF_PI;
        Quaternionf q = new Quaternionf().rotationY(yaw);
        if (upright) q.rotateX(side);
        else if (!flat) q.rotateZ(side);
        c.entityData.set(ORIENT, q);
        c.entityData.set(SETTLED, true);
        c.checkedUpright = true;
        AABB box = c.makeBoundingBox();
        double half = box.getYsize() / 2.0;
        c.setPos(ground.x, ground.y + half - sink * 2 * half, ground.z);
        return c;
    }

    /**
     * A body frozen where it stood: upright with its feet at {@code feet}, facing {@code yaw}
     * (radians, 0 = +z), leaning forward {@code lean} and sideways {@code tilt}, its limbs held
     * in {@code pose} (part name -> [x, y, z] angles; parts left out hang as they would at rest).
     */
    public static CorpseEntity figure(ServerLevel level, EntityType<?> mob, Vec3 feet, float yaw, float lean, float tilt, int material,
                                      CompoundTag pose) {
        CorpseEntity c = remains(level, mob, feet, net.minecraft.util.RandomSource.create(0), material, 0);
        if (c == null) return null;
        Quaternionf q = new Quaternionf().rotationY(yaw).rotateX(lean).rotateZ(tilt);
        c.entityData.set(ORIENT, q);
        c.entityData.set(FIGURE, pose.copy());
        Vector3f mid = q.transform(new Vector3f(0, c.bodyHeight() / 2F, 0));
        c.setPos(feet.x + mid.x, feet.y + mid.y, feet.z + mid.z);
        c.setBoundingBox(c.makeBoundingBox());
        return c;
    }

    /** The pose a frozen figure's limbs are held in; empty for an ordinary body. */
    public CompoundTag figurePose() {
        return entityData.get(FIGURE);
    }

    /** Puts a piece of gear on the remains, which can be taken off. */
    public void giveGear(EquipmentSlot slot, ItemStack stack) {
        entityData.set(EQUIP[slot.ordinal()], stack);
        int mask = entityData.get(LOOT_MASK);
        entityData.set(LOOT_MASK, stack.isEmpty() ? mask & ~(1 << slot.ordinal()) : mask | (1 << slot.ordinal()));
    }

    /** Sticks an old arrow somewhere in the body (it can be pulled out). */
    public void addOldArrow(net.minecraft.util.RandomSource rand) {
        ListTag list = arrows().copy();
        if (list.size() >= MAX_ARROWS) return;
        float w = bodyWidth(), h = bodyHeight(), d = bodyDepth();
        CompoundTag t = new CompoundTag();
        t.putFloat("X", (rand.nextFloat() - 0.5F) * w * 0.5F);
        t.putFloat("Y", h * (0.35F + rand.nextFloat() * 0.5F));
        t.putFloat("Z", (rand.nextFloat() - 0.5F) * d * 0.5F);
        Vector3f dir = new Vector3f(rand.nextFloat() - 0.5F, -0.2F - rand.nextFloat() * 0.5F, rand.nextFloat() - 0.5F).normalize();
        t.putFloat("DX", dir.x);
        t.putFloat("DY", dir.y);
        t.putFloat("DZ", dir.z);
        t.putInt("Kind", StuckArrows.KIND_ARROW);
        t.putInt("Color", -1);
        t.putBoolean("Loot", true);
        list.add(t);
        CompoundTag w2 = new CompoundTag();
        w2.put("List", list);
        entityData.set(ARROWS, w2);
    }

    /**
     * Something growing on (or lying over) the remains: a mushroom, moss, roots, snow, cobweb.
     * It goes on whichever side of the body faces up, at a random spot.
     */
    public void addGrowth(net.minecraft.world.level.block.Block block, float scale, net.minecraft.util.RandomSource rand) {
        Quaternionf inv = new Quaternionf(orientation()).conjugate();
        Vector3f up = inv.transform(new Vector3f(0, 1, 0));
        float[] half = {bodyWidth() / 2F, bodyHeight() / 2F, bodyDepth() / 2F};
        float[] u = {up.x, up.y, up.z};
        int axis = 0;
        for (int i = 1; i < 3; i++) if (Math.abs(u[i]) > Math.abs(u[axis])) axis = i;
        float[] p = new float[3];
        for (int i = 0; i < 3; i++) {
            p[i] = i == axis ? Math.signum(u[i]) * half[i] * 0.45F : (rand.nextFloat() * 2 - 1) * half[i] * 0.6F;
        }
        ListTag list = growths().copy();
        CompoundTag t = new CompoundTag();
        t.putString("Block", String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block)));
        t.putFloat("X", p[0]);
        t.putFloat("Y", p[1]);
        t.putFloat("Z", p[2]);
        t.putFloat("Scale", scale);
        t.putFloat("Yaw", rand.nextFloat() * 360F);
        list.add(t);
        CompoundTag w = new CompoundTag();
        w.put("List", list);
        entityData.set(DECOR, w);
    }

    /** Growths on the body: block id, position from the body's middle in its own frame, scale, yaw. */
    public ListTag growths() {
        return entityData.get(DECOR).getList("List", Tag.TAG_COMPOUND);
    }

    /** How far through decay a burnt body jumps to: charred, just short of bare bone. */
    public static final float BURNT_DECAY = 0.75F;

    /**
     * Chars the body: it jumps to the stage just before skeleton (blackened instead of rotten),
     * burns for a few seconds and smokes for a while after.
     */
    public void burn() {
        if (level().isClientSide) return;
        CorpseKind kind = kind();
        if (kind == CorpseKind.NO_DECAY || kind == CorpseKind.REMAINS) return;
        if (burnTime() > 0) return;
        long now = level().getGameTime();
        entityData.set(BURN_TIME, now);
        if (kind == CorpseKind.FLESH || kind == CorpseKind.NO_BONES) {
            long target = (long) (BURNT_DECAY * decayTicks());
            if (now - deathTime() < target) entityData.set(DEATH_TIME, now - target);
        }
        setSecondsOnFire(4);
    }

    /** Game time the body was burnt, or 0 if it never was. */
    public long burnTime() {
        return entityData.get(BURN_TIME);
    }

    public boolean burnt() {
        return burnTime() > 0;
    }

    // ---- getters used by the renderer ----------------------------------------------------------

    public String mobType() {
        return entityData.get(MOB_TYPE);
    }

    /** A squid rots away to nothing but its beak and its pen, which lie there like bones. */
    public boolean leavesBeakAndPen() {
        return CorpseKind.leavesBeakAndPen(mobType());
    }

    public CompoundTag renderTag() {
        return entityData.get(RENDER_TAG);
    }

    public float bodyWidth() {
        return entityData.get(BODY_W);
    }

    public float bodyHeight() {
        return entityData.get(BODY_H);
    }

    public float bodyDepth() {
        return entityData.get(BODY_D);
    }

    public Quaternionf orientation() {
        return entityData.get(ORIENT);
    }

    public boolean settled() {
        return entityData.get(SETTLED);
    }

    public CorpseKind kind() {
        return CorpseKind.byId(entityData.get(KIND));
    }

    public ItemStack equipment(EquipmentSlot slot) {
        return entityData.get(EQUIP[slot.ordinal()]);
    }

    public boolean lootable(EquipmentSlot slot) {
        return (entityData.get(LOOT_MASK) & (1 << slot.ordinal())) != 0;
    }

    public ListTag arrows() {
        return entityData.get(ARROWS).getList("List", Tag.TAG_COMPOUND);
    }

    public long deathTime() {
        return entityData.get(DEATH_TIME);
    }

    /** Game time the body died at; used by tests to age a corpse. */
    public void setDeathTime(long time) {
        entityData.set(DEATH_TIME, time);
    }

    public int decayTicks() {
        return entityData.get(DECAY_TICKS);
    }

    /** 0 = fresh, 1 = bare skeleton. */
    public float decay(float partialTick) {
        double age = level().getGameTime() - deathTime() + partialTick;
        return (float) Mth.clamp(age / Math.max(1, decayTicks()), 0.0, 1.0);
    }

    // ---- ticking --------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            if (clientTick != null) clientTick.accept(this);
            return;
        }
        if (settled() && !checkedUpright) {
            // a body left standing (wedged against something) gets another chance to fall over
            checkedUpright = true;
            Vector3f up = orientation().transform(new Vector3f(0, 1, 0));
            if (up.y > 0.85F && !flatBody() && cause() != DeathCause.CRUSHED && kind() != CorpseKind.REMAINS) {
                wake();
                double a = random.nextDouble() * Math.PI * 2;
                ragdoll.collapse(Math.cos(a), Math.sin(a), Math.max(0.04, 1.35 * 0.08 * bodyWidth() / bodyHeight()), 6, 60, 0.55);
            }
        }
        if (!settled()) {
            if (ragdoll == null) {
                ragdoll = makeRagdoll();
                ragdoll.place(position(), orientation(), Vec3.ZERO, new Vector3f());
            }
            boolean rest = ragdoll.tick(level());
            setPos(ragdoll.center());
            entityData.set(ORIENT, ragdoll.orientation());
            double impact = ragdoll.takeImpact();
            if (impact > 0.15) {
                float vol = (float) Math.min(1.0, impact) * 0.7F;
                playSound(bodyWidth() * bodyHeight() > 1.2F ? SoundEvents.GENERIC_BIG_FALL : SoundEvents.GENERIC_SMALL_FALL, vol, 0.8F);
            }
            if (ragdoll.inLava()) {
                burnUp();
                return;
            }
            if (rest) {
                entityData.set(SETTLED, true);
            }
        }
        if (tickCount % 20 == 0 && kind() != CorpseKind.REMAINS) {
            long age = level().getGameTime() - deathTime();
            long end = decayTicks() + skeletonTicks;
            CorpseKind kind = kind();
            if (kind == CorpseKind.NO_BONES && age >= decayTicks() && !leavesBeakAndPen()) {
                discard(); // faded away
            } else if (skeletonTicks > 0 && age >= end) {
                crumble(false);
            }
        }
    }

    private void burnUp() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 12, bodyWidth() / 2, 0.2, bodyWidth() / 2, 0.02);
            playSound(SoundEvents.GENERIC_BURN, 0.6F, 1.0F);
        }
        discard();
    }

    /** Breaks the corpse up into bits (or bone dust), dropping any loot left on it. */
    public void crumble(boolean dropLoot) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (dropLoot) dropAllLoot();
        boolean mummy = mummy() != 0 && (kind() == CorpseKind.REMAINS || decay(0) > 0.5F);
        if (kind() == CorpseKind.REMAINS && dropLoot) {
            spawnAtLocation(new ItemStack(Items.BONE, (mummy ? 1 : 2) + random.nextInt(mummy ? 3 : 4)), 0.3F);
            if (mummy) spawnAtLocation(new ItemStack(Items.LEATHER, 1 + random.nextInt(2)), 0.3F); // the dried skin
        }
        boolean bones = !mummy && (kind() == CorpseKind.BONES || kind() == CorpseKind.REMAINS || decay(0) > 0.75F);
        ParticleOptions particle = mummy ? new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.LEATHER))
                : bones ? new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState())
                : new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH));
        AABB box = getBoundingBox();
        sl.sendParticles(particle, getX(), getY(), getZ(), 30, box.getXsize() / 3, box.getYsize() / 3, box.getZsize() / 3, 0.05);
        playSound(bones ? SoundEvents.BONE_BLOCK_BREAK : mummy ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.HONEY_BLOCK_BREAK, 0.8F, 0.9F);
        level().broadcastEntityEvent(this, EVENT_CRUMBLE);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CRUMBLE) {
            if (clientCrumble != null) clientCrumble.accept(this);
            return;
        }
        super.handleEntityEvent(id);
    }

    // ---- interaction ----------------------------------------------------------------------------

    /** Right click: take whatever gear and arrows can be taken off the body. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!hasLoot()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            dropAllLoot();
            playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.8F, 0.9F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public boolean hasLoot() {
        if (entityData.get(LOOT_MASK) != 0 || !extraLoot.isEmpty()) return true;
        for (Tag t : arrows()) {
            if (((CompoundTag) t).getBoolean("Loot")) return true;
        }
        return false;
    }

    private void dropAllLoot() {
        int mask = entityData.get(LOOT_MASK);
        for (int i = 0; i < 6; i++) {
            if ((mask & (1 << i)) == 0) continue;
            ItemStack s = entityData.get(EQUIP[i]);
            if (!s.isEmpty()) spawnAtLocation(s.copy(), 0.3F);
            entityData.set(EQUIP[i], ItemStack.EMPTY);
        }
        entityData.set(LOOT_MASK, 0);
        if (!extraLoot.isEmpty()) {
            CompoundTag render = renderTag().copy();
            for (String key : extraLoot.getAllKeys()) {
                ItemStack s = ItemStack.of(extraLoot.getCompound(key));
                if (!s.isEmpty()) spawnAtLocation(s, 0.3F);
                render.remove(key);
            }
            extraLoot = new CompoundTag();
            entityData.set(RENDER_TAG, render);
        }
        ListTag kept = new ListTag();
        boolean changed = false;
        for (Tag t : arrows()) {
            CompoundTag a = (CompoundTag) t;
            if (a.getBoolean("Loot")) {
                spawnAtLocation(StuckArrows.item(a), 0.3F);
                changed = true;
            } else {
                kept.add(a);
            }
        }
        if (changed) {
            CompoundTag w = new CompoundTag();
            w.put("List", kept);
            entityData.set(ARROWS, w);
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /**
     * Hitting the body shoves it around (it ragdolls again); hitting it while sneaking breaks it
     * up. Arrows that hit it stay stuck in it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            burn();
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && p.isShiftKeyDown() && direct == p) {
            crumble(true);
            return true;
        }
        Vec3 dir;
        double strength;
        if (direct instanceof AbstractArrow arrow && !(direct instanceof ThrownTrident) && arrow.getPierceLevel() <= 0) {
            stickArrow(arrow);
            dir = arrow.getDeltaMovement().normalize();
            strength = 0.08;
        } else if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getSourcePosition() != null) {
            dir = position().subtract(source.getSourcePosition()).normalize().add(0, 0.6, 0).normalize();
            strength = Math.min(1.2, amount * 0.06);
        } else if (attacker != null) {
            Vec3 look = attacker.getLookAngle();
            dir = new Vec3(look.x, Math.max(0.15, look.y + 0.3), look.z).normalize();
            strength = Math.min(0.5, 0.15 + amount * 0.04);
        } else {
            return false;
        }
        wake();
        ragdoll.push(dir.scale(strength), true);
        if (ragdoll.uprightness() > 0.7 && !flatBody()) ragdoll.collapse(dir.x, dir.z, 0.05, 6, 40, 0.55);
        return true;
    }

    private boolean flatBody() {
        return bodyWidth() >= bodyHeight() * 1.5F;
    }

    private void wake() {
        if (ragdoll == null) {
            ragdoll = makeRagdoll();
            ragdoll.place(position(), orientation(), Vec3.ZERO, new Vector3f());
        }
        entityData.set(SETTLED, false);
    }

    private void stickArrow(AbstractArrow arrow) {
        ListTag list = arrows().copy();
        if (list.size() >= MAX_ARROWS) return;
        Vec3 vel = arrow.getDeltaMovement();
        if (vel.lengthSqr() < 1e-6) return;
        AABB box = getBoundingBox().inflate(0.05);
        Vec3 from = arrow.position().subtract(vel.normalize().scale(2));
        Vec3 hit = box.clip(from, arrow.position().add(vel.scale(2))).orElse(position());
        // into the body's own upright frame (feet at the origin), so the arrow moves with it
        Quaternionf inv = new Quaternionf(orientation()).conjugate();
        Vector3f local = inv.transform(new Vector3f((float) (hit.x - getX()), (float) (hit.y - getY()), (float) (hit.z - getZ())));
        local.y += bodyHeight() / 2F;
        Vector3f dir = inv.transform(new Vector3f((float) vel.x, (float) vel.y, (float) vel.z)).normalize();
        list.add(StuckArrows.record(arrow, local, dir));
        CompoundTag w = new CompoundTag();
        w.put("List", list);
        entityData.set(ARROWS, w);
    }

    // ---- bounds -----------------------------------------------------------------------------------

    @Override
    protected AABB makeBoundingBox() {
        if (!ready) return super.makeBoundingBox();
        float hw = bodyWidth() / 2F, hh = bodyHeight() * squash() / 2F, hd = bodyDepth() / 2F;
        Quaternionf q = orientation();
        Vector3f v = new Vector3f();
        float ex = 0, ey = 0, ez = 0;
        for (int i = 0; i < 8; i++) {
            v.set((i & 1) != 0 ? hw : -hw, (i & 2) != 0 ? hh : -hh, (i & 4) != 0 ? hd : -hd);
            q.transform(v);
            ex = Math.max(ex, Math.abs(v.x));
            ey = Math.max(ey, Math.abs(v.y));
            ez = Math.max(ez, Math.abs(v.z));
        }
        Vec3 c = position();
        return new AABB(c.x - ex, c.y - ey, c.z - ez, c.x + ex, c.y + ey, c.z + ez);
    }

    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        return getPosition(partialTicks).add(0, 0.25, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        double d = 64.0 * getViewScale();
        return distSqr < d * d;
    }

    // ---- saving -----------------------------------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(MOB_TYPE, tag.getString("MobType"));
        entityData.set(RENDER_TAG, tag.getCompound("Render"));
        entityData.set(BODY_W, tag.getFloat("BodyW"));
        entityData.set(BODY_H, tag.getFloat("BodyH"));
        entityData.set(BODY_D, tag.contains("BodyD") ? tag.getFloat("BodyD") : tag.getFloat("BodyW"));
        ListTag o = tag.getList("Orient", Tag.TAG_FLOAT);
        if (o.size() == 4) {
            entityData.set(ORIENT, new Quaternionf(o.getFloat(0), o.getFloat(1), o.getFloat(2), o.getFloat(3)).normalize());
        }
        entityData.set(SETTLED, tag.getBoolean("Settled"));
        entityData.set(DEATH_TIME, tag.getLong("DeathTime"));
        entityData.set(BURN_TIME, tag.getLong("BurnTime"));
        entityData.set(CAUSE, tag.getByte("Cause"));
        entityData.set(SQUASH, tag.contains("Squash") ? tag.getFloat("Squash") : 1F);
        entityData.set(MATERIAL, tag.getByte("Material"));
        entityData.set(DECAY_TICKS, Math.max(20, tag.getInt("DecayTicks")));
        skeletonTicks = tag.getLong("SkeletonTicks");
        entityData.set(KIND, tag.getByte("Kind"));
        entityData.set(LOOT_MASK, tag.getInt("LootMask"));
        ListTag eq = tag.getList("Equipment", Tag.TAG_COMPOUND);
        for (int i = 0; i < 6 && i < eq.size(); i++) entityData.set(EQUIP[i], ItemStack.of(eq.getCompound(i)));
        CompoundTag arrows = new CompoundTag();
        arrows.put("List", tag.getList("Arrows", Tag.TAG_COMPOUND));
        entityData.set(ARROWS, arrows);
        CompoundTag decor = new CompoundTag();
        decor.put("List", tag.getList("Growths", Tag.TAG_COMPOUND));
        entityData.set(DECOR, decor);
        entityData.set(MUMMY, tag.getByte("Mummy"));
        entityData.set(FIGURE, tag.getCompound("Figure"));
        extraLoot = tag.getCompound("ExtraLoot");
        ragdoll = null;
        setBoundingBox(makeBoundingBox());
        lookVersion++;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("MobType", mobType());
        if (!figurePose().isEmpty()) tag.put("Figure", figurePose());
        tag.put("Render", renderTag());
        tag.putFloat("BodyW", bodyWidth());
        tag.putFloat("BodyH", bodyHeight());
        tag.putFloat("BodyD", bodyDepth());
        Quaternionf q = orientation();
        ListTag o = new ListTag();
        o.add(net.minecraft.nbt.FloatTag.valueOf(q.x));
        o.add(net.minecraft.nbt.FloatTag.valueOf(q.y));
        o.add(net.minecraft.nbt.FloatTag.valueOf(q.z));
        o.add(net.minecraft.nbt.FloatTag.valueOf(q.w));
        tag.put("Orient", o);
        tag.putBoolean("Settled", settled());
        tag.putLong("DeathTime", deathTime());
        tag.putLong("BurnTime", burnTime());
        tag.putByte("Cause", entityData.get(CAUSE));
        tag.putFloat("Squash", squash());
        tag.putByte("Material", entityData.get(MATERIAL));
        tag.putInt("DecayTicks", decayTicks());
        tag.putLong("SkeletonTicks", skeletonTicks);
        tag.putByte("Kind", entityData.get(KIND));
        tag.putInt("LootMask", entityData.get(LOOT_MASK));
        ListTag eq = new ListTag();
        for (int i = 0; i < 6; i++) eq.add(entityData.get(EQUIP[i]).save(new CompoundTag()));
        tag.put("Equipment", eq);
        tag.put("Arrows", arrows());
        tag.put("Growths", growths());
        tag.putByte("Mummy", entityData.get(MUMMY));
        tag.put("ExtraLoot", extraLoot);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}

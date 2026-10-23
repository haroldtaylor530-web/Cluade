package com.harold.corpsedecay.corpse;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The bones of a whale lying on the sea floor: one entity for the whole skeleton, drawn as a full
 * skeleton (backbone, ribs, skull, jaws, flippers) with what's growing on it. Which bones have been
 * knocked loose is fixed by its seed. It lies still and never crumbles; sneak and hit it to break
 * it up for bones.
 */
public class WhaleRemainsEntity extends Entity {
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(WhaleRemainsEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(WhaleRemainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(WhaleRemainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(WhaleRemainsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WHOLE = SynchedEntityData.defineId(WhaleRemainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLESH = SynchedEntityData.defineId(WhaleRemainsEntity.class, EntityDataSerializers.FLOAT);

    /** blue whale, humpback (long flippers), sperm whale (basin skull, toothed jaw), beaked whale (long narrow beak, crested skull) */
    public static final int BLUE = 0, HUMPBACK = 1, SPERM = 2, BEAKED = 3;

    private final boolean ready;

    public WhaleRemainsEntity(EntityType<? extends WhaleRemainsEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        ready = true;
    }

    /** A whale of this kind and length lying with its head pointing {@code facing} radians round from +x. */
    public void setup(int kind, float length, float facing, int seed, float whole, float flesh) {
        entityData.set(FLESH, flesh);
        entityData.set(KIND, (byte) kind);
        entityData.set(LENGTH, length);
        entityData.set(FACING, facing);
        entityData.set(SEED, seed);
        entityData.set(WHOLE, whole);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, (byte) 0);
        entityData.define(LENGTH, 24F);
        entityData.define(FACING, 0F);
        entityData.define(SEED, 0);
        entityData.define(WHOLE, 1F);
        entityData.define(FLESH, 0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ready) setBoundingBox(makeBoundingBox());
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public float length() {
        return entityData.get(LENGTH);
    }

    /** Which way the head points: radians round from +x towards +z. */
    public float facing() {
        return entityData.get(FACING);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    /** 1 = every bone in place; less = more scattered by scavengers. */
    public float whole() {
        return entityData.get(WHOLE);
    }

    /** How much of the whale's flesh is still on its bones: 0 = bare bones. */
    public float flesh() {
        return entityData.get(FLESH);
    }

    @Override
    protected AABB makeBoundingBox() {
        if (!ready) return super.makeBoundingBox();
        float l = length();
        double hl = l / 2 + 1, hw = l * (kind() == HUMPBACK ? 0.36 : 0.22);
        double c = Math.cos(facing()), s = Math.sin(facing());
        double ex = Math.abs(c) * hl + Math.abs(s) * hw, ez = Math.abs(s) * hl + Math.abs(c) * hw;
        return new AABB(getX() - ex, getY(), getZ() - ez, getX() + ex, getY() + Math.max(1.5, l * 0.16), getZ() + ez);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
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

    /** Sneak and hit it to break the skeleton up for bones. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        if (!(source.getEntity() instanceof Player p) || !p.isShiftKeyDown() || source.getDirectEntity() != p) return false;
        breakUp();
        return true;
    }

    public void breakUp() {
        if (!(level() instanceof ServerLevel sl)) return;
        int bones = Math.round(length());
        for (int i = 0; i < bones; i += 16) spawnAtLocation(new ItemStack(Items.BONE, Math.min(16, bones - i)), 0.5F);
        spawnAtLocation(new ItemStack(Items.BONE_BLOCK, 1 + Math.round(length() / 8)), 0.5F);
        AABB b = getBoundingBox();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 1, getZ(),
                80, b.getXsize() / 4, 0.8, b.getZsize() / 4, 0.05);
        playSound(SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.6F);
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        double d = 160.0 * getViewScale();
        return distSqr < d * d;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getByte("Kind"));
        entityData.set(LENGTH, tag.contains("Length") ? tag.getFloat("Length") : 24F);
        entityData.set(FACING, tag.getFloat("Facing"));
        entityData.set(SEED, tag.getInt("Seed"));
        entityData.set(WHOLE, tag.contains("Whole") ? tag.getFloat("Whole") : 1F);
        entityData.set(FLESH, tag.getFloat("Flesh"));
        setBoundingBox(makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Kind", (byte) kind());
        tag.putFloat("Length", length());
        tag.putFloat("Facing", facing());
        tag.putInt("Seed", seed());
        tag.putFloat("Whole", whole());
        tag.putFloat("Flesh", flesh());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}

package com.harold.realisticsurvival.entity;

import com.harold.realisticsurvival.ModRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * A tree that was cut through and is toppling over. It carries the tree's blocks (relative to
 * the cut), rotates them down around the stump over a second or two, then crashes: logs and
 * leaf drops land where the tree hit the ground and anything underneath gets hurt.
 */
public class FallingTree extends Entity implements IEntityWithComplexSpawn {
    public record Part(BlockPos offset, BlockState state) {
        static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Part::offset),
                BlockState.CODEC.fieldOf("state").forGetter(Part::state)
        ).apply(i, Part::new));
    }

    /** Ticks for the trunk to settle into the cut before it starts to tip. */
    public static final int SLUMP_TICKS = 4;

    private List<Part> parts = new ArrayList<>();
    private float dirX, dirZ = 1.0F;
    private int fallTicks = 30;
    public int age;
    private float extent = 8.0F;

    public FallingTree(EntityType<? extends FallingTree> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** dirX/dirZ: the horizontal direction the tree falls in. pivot is the bottom-centre of the cut log. */
    public static FallingTree create(ServerLevel level, BlockPos cut, List<Part> parts, float dirX, float dirZ) {
        FallingTree tree = new FallingTree(ModRegistry.FALLING_TREE.get(), level);
        tree.setPos(cut.getX() + 0.5, cut.getY(), cut.getZ() + 0.5);
        tree.setParts(parts);
        float len = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        tree.dirX = len < 1e-4F ? 1.0F : dirX / len;
        tree.dirZ = len < 1e-4F ? 0.0F : dirZ / len;
        int height = 0;
        for (Part p : parts) height = Math.max(height, p.offset().getY());
        tree.fallTicks = Mth.clamp(24 + height, 26, 50);
        return tree;
    }

    private void setParts(List<Part> parts) {
        this.parts = parts;
        float e = 2.0F;
        for (Part p : parts) {
            BlockPos o = p.offset();
            e = Math.max(e, Math.max(Math.abs(o.getX()), Math.max(Math.abs(o.getY()), Math.abs(o.getZ()))) + 1.0F);
        }
        this.extent = e;
    }

    public List<Part> parts() {
        return parts;
    }

    public float dirX() {
        return dirX;
    }

    public float dirZ() {
        return dirZ;
    }

    public float extent() {
        return extent;
    }

    /** How far it has tipped over, 0 (upright) to 90 degrees (on the ground), accelerating as it goes. */
    public float angle(float partialTicks) {
        float t = (age + partialTicks - SLUMP_TICKS) / (fallTicks - SLUMP_TICKS);
        if (t <= 0) return 0.0F;
        return Math.min(90.0F, 90.0F * t * t);
    }

    /** How far it has sunk into the cut, 0 to 1 block. */
    public float slump(float partialTicks) {
        return Math.min(1.0F, (age + partialTicks) / SLUMP_TICKS);
    }

    @Override
    public void tick() {
        age++;
        if (level() instanceof ServerLevel level) {
            if (age == 1) {
                level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7F, 0.6F);
            }
            if (age == fallTicks / 2) {
                level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
            }
            if (age >= fallTicks) {
                land(level);
                discard();
            }
        }
    }

    /** Where a part ends up once the tree lies flat. */
    public Vec3 landedCentre(Part part) {
        // centre of the block relative to the pivot, after sinking into the cut
        double vx = part.offset().getX(), vy = part.offset().getY() - 1.0 + 0.5, vz = part.offset().getZ();
        // rotate 90 degrees about the axis (dirZ, 0, -dirX): v' = (a x v) + a (a . v)
        double ax = dirZ, az = -dirX;
        double cx = -az * vy, cy = az * vx - ax * vz, cz = ax * vy;
        double dot = ax * vx + az * vz;
        return new Vec3(getX() + cx + ax * dot, getY() + cy + 0.5, getZ() + cz + az * dot);
    }

    private void land(ServerLevel level) {
        int effects = 0;
        Vec3 crash = position();
        double far = 0;
        DamageSource crush = level.damageSources().fallingBlock(this);
        for (Part part : parts) {
            Vec3 c = landedCentre(part);
            BlockPos bp = BlockPos.containing(c);
            // don't drop things inside the ground
            for (int i = 0; i < 8 && !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty(); i++) bp = bp.above();
            BlockState state = part.state();
            boolean log = state.is(BlockTags.LOGS);
            List<ItemStack> drops = new ArrayList<>();
            if (log) {
                drops.add(new ItemStack(state.getBlock().asItem()));
                AABB box = new AABB(bp).inflate(0.2);
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    victim.hurtServer(level, crush, 8.0F);
                }
            } else {
                drops.addAll(Block.getDrops(state, level, bp, null));
                if (state.is(BlockTags.LEAVES) && level.getRandom().nextFloat() < 0.15F) drops.add(new ItemStack(Items.STICK));
            }
            for (ItemStack stack : drops) {
                if (stack.isEmpty()) continue;
                ItemEntity item = new ItemEntity(level, bp.getX() + 0.5, bp.getY() + 0.3, bp.getZ() + 0.5, stack);
                item.setDeltaMovement(level.getRandom().nextGaussian() * 0.05, 0.15, level.getRandom().nextGaussian() * 0.05);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
            if ((log || level.getRandom().nextInt(5) == 0) && effects < 40) {
                level.levelEvent(2001, bp, Block.getId(state));
                effects++;
            }
            double d = c.distanceToSqr(position());
            if (d > far) {
                far = d;
                crash = c;
            }
        }
        Vec3 mid = position().add(crash).scale(0.5);
        level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.6F, 0.45F);
        level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.5F, 0.7F);
    }

    // ---- entity plumbing ---------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 96.0 + extent;
        return distance < d * d;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        setParts(new ArrayList<>(in.read("parts", Part.CODEC.listOf()).orElse(List.of())));
        dirX = in.getFloatOr("dirX", 1.0F);
        dirZ = in.getFloatOr("dirZ", 0.0F);
        fallTicks = in.getIntOr("fallTicks", 30);
        age = in.getIntOr("age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("parts", Part.CODEC.listOf(), parts);
        out.putFloat("dirX", dirX);
        out.putFloat("dirZ", dirZ);
        out.putInt("fallTicks", fallTicks);
        out.putInt("age", age);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(dirX);
        buf.writeFloat(dirZ);
        buf.writeVarInt(fallTicks);
        buf.writeVarInt(age);
        buf.writeVarInt(parts.size());
        for (Part p : parts) {
            buf.writeByte(p.offset().getX());
            buf.writeVarInt(p.offset().getY());
            buf.writeByte(p.offset().getZ());
            buf.writeVarInt(Block.getId(p.state()));
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        dirX = buf.readFloat();
        dirZ = buf.readFloat();
        fallTicks = buf.readVarInt();
        age = buf.readVarInt();
        int n = buf.readVarInt();
        List<Part> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int x = buf.readByte(), y = buf.readVarInt(), z = buf.readByte();
            list.add(new Part(new BlockPos(x, y, z), Block.stateById(buf.readVarInt())));
        }
        setParts(list);
    }
}

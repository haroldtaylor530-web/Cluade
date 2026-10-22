package com.harold.corpsedecay.corpse;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Arrows that hit a mob are remembered (where they went in, and at what angle) in the mob's own
 * data, so they're still sticking out of the body after it dies. Positions are in the mob's
 * upright frame: feet at the origin, facing +z.
 */
public final class StuckArrows {
    public static final String KEY = "corpsedecay_arrows";

    public static final int KIND_ARROW = 0;
    public static final int KIND_SPECTRAL = 1;

    private StuckArrows() {
    }

    public static CompoundTag record(AbstractArrow arrow, Vector3f local, Vector3f dir) {
        CompoundTag t = new CompoundTag();
        t.putFloat("X", local.x);
        t.putFloat("Y", local.y);
        t.putFloat("Z", local.z);
        t.putFloat("DX", dir.x);
        t.putFloat("DY", dir.y);
        t.putFloat("DZ", dir.z);
        t.putInt("Kind", arrow instanceof SpectralArrow ? KIND_SPECTRAL : KIND_ARROW);
        if (arrow instanceof Arrow a) {
            t.putInt("Color", a.getColor());
            CompoundTag save = new CompoundTag();
            a.addAdditionalSaveData(save);
            if (save.contains("Potion")) t.putString("Potion", save.getString("Potion"));
        }
        t.putBoolean("Loot", arrow.pickup == AbstractArrow.Pickup.ALLOWED);
        return t;
    }

    /** Remembers an arrow that just hurt a living mob. */
    public static void onArrowHit(LivingEntity mob, AbstractArrow arrow) {
        net.minecraft.world.phys.Vec3 vel = arrow.getDeltaMovement();
        if (vel.lengthSqr() < 1e-6) return;
        CompoundTag data = mob.getPersistentData();
        ListTag list = data.getList(KEY, Tag.TAG_COMPOUND);
        if (list.size() >= CorpseEntity.MAX_ARROWS) return;
        net.minecraft.world.phys.AABB box = mob.getBoundingBox().inflate(0.05);
        net.minecraft.world.phys.Vec3 dirN = vel.normalize();
        net.minecraft.world.phys.Vec3 hit = box.clip(arrow.position().subtract(dirN.scale(2)), arrow.position().add(vel.scale(2)))
                .orElseGet(() -> {
                    // already inside the box: use the closest point on the mob's middle line
                    net.minecraft.world.phys.Vec3 c = box.getCenter();
                    return new net.minecraft.world.phys.Vec3(c.x, Mth.clamp(arrow.getY(), box.minY, box.maxY), c.z);
                });
        float yaw = mob.yBodyRot * Mth.DEG_TO_RAD;
        Vector3f local = new Vector3f((float) (hit.x - mob.getX()), (float) (hit.y - mob.getY()), (float) (hit.z - mob.getZ()));
        new org.joml.Quaternionf().rotationY(yaw).transform(local);
        Vector3f dir = new org.joml.Quaternionf().rotationY(yaw).transform(new Vector3f((float) dirN.x, (float) dirN.y, (float) dirN.z));
        list.add(record(arrow, local, dir));
        data.put(KEY, list);
    }

    public static ListTag of(LivingEntity mob) {
        return mob.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy();
    }

    /** The item you get back pulling this arrow out. */
    public static ItemStack item(CompoundTag a) {
        if (a.getInt("Kind") == KIND_SPECTRAL) return new ItemStack(Items.SPECTRAL_ARROW);
        if (a.contains("Potion")) {
            ItemStack s = new ItemStack(Items.TIPPED_ARROW);
            var potion = ForgeRegistries.POTIONS.getValue(new ResourceLocation(a.getString("Potion")));
            if (potion != null) PotionUtils.setPotion(s, potion);
            return s;
        }
        return new ItemStack(Items.ARROW);
    }
}

package com.harold.realisticsurvival.entity;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown spear. Sticks in what it hits and can be picked back up. */
public class ThrownSpear extends AbstractArrow {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ThrownSpear.class, EntityDataSerializers.ITEM_STACK);

    private float damage = 7.0F;
    private boolean dealtDamage;

    public ThrownSpear(EntityType<? extends ThrownSpear> type, Level level) {
        super(type, level);
    }

    public ThrownSpear(Level level, LivingEntity owner, ItemStack stack, float damage) {
        super(ModRegistry.THROWN_SPEAR.get(), owner, level, stack, null);
        this.damage = damage;
        this.entityData.set(ITEM, stack.copy());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM, ItemStack.EMPTY);
    }

    /** The spear item, for rendering. */
    public ItemStack getItem() {
        return this.entityData.get(ITEM);
    }

    @Override
    public void tick() {
        if (!level().isClientSide() && getItem().isEmpty()) {
            this.entityData.set(ITEM, getPickupItemStackOrigin().copy());
        }
        if (inGroundTime > 4) dealtDamage = true;
        super.tick();
    }

    @Override
    protected EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
        return dealtDamage ? null : super.findHitEntity(from, to);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        DamageSource source = damageSources().trident(this, owner == null ? this : owner);
        dealtDamage = true;
        if (level() instanceof ServerLevel serverLevel) {
            float dmg = damage * (float) Math.min(1.25, getDeltaMovement().length() / 2.0);
            if (target.hurtServer(serverLevel, source, Math.max(1.0F, dmg))) {
                if (target instanceof LivingEntity living) {
                    if (Config.INJURIES.getAsBoolean() && random.nextFloat() < 0.35F) {
                        living.addEffect(new MobEffectInstance(ModRegistry.BLEEDING, 20 * 20, 0));
                    }
                    doPostHurtEffects(living);
                }
                ItemStack weapon = getPickupItemStackOrigin();
                if (weapon.isDamageableItem()) {
                    weapon.setDamageValue(Math.min(weapon.getMaxDamage() - 1, weapon.getDamageValue() + 1));
                    setPickupItemStack(weapon);
                }
            }
        }
        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModRegistry.FLINT_SPEAR.get());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    protected float getWaterInertia() {
        return 0.9F;
    }
}

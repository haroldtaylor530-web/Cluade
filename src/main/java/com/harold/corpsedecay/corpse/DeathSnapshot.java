package com.harold.corpsedecay.corpse;

import java.util.Set;

import com.harold.corpsedecay.CorpseKind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Everything a corpse needs to know about the mob, captured the moment it dies. */
public final class DeathSnapshot {
    /** Save-data keys that don't affect how the mob looks (or would make the body act alive). */
    private static final Set<String> STRIP = Set.of(
            "Pos", "Motion", "Rotation", "UUID", "Passengers", "Leash", "Brain", "Attributes", "ActiveEffects",
            "Fire", "Air", "HurtTime", "HurtByTimestamp", "DeathTime", "FallDistance", "FallFlying", "OnGround",
            "PortalCooldown", "Invulnerable", "Offers", "Gossips", "Inventory", "ArmorItems", "HandItems",
            "ArmorDropChances", "HandDropChances", "CustomName", "CustomNameVisible", "ForgeData", "ForgeCaps",
            "TicksFrozen", "Health", "AbsorptionAmount", "CanPickUpLoot", "PersistenceRequired", "Xp",
            "LastRestock", "RestocksToday", "LastGossipDecay", "AngerTime", "AngryAt", "Items", "Tags",
            "SleepingX", "SleepingY", "SleepingZ", "NoAI", "Silent", "Glowing", "HasVisualFire", "Owner",
            "DrownedConversionTime", "InWaterTime", "ConversionTime", "Bred", "InLove", "LoveCause");

    public String type;
    public CompoundTag renderTag;
    public float width, height, bodyYaw;
    public Vec3 feet, velocity, push;
    public final ItemStack[] equipment = new ItemStack[6];
    public final boolean[] lootable = new boolean[6];
    public ListTag arrows;
    public CompoundTag extraLoot = new CompoundTag();
    public Component name;
    public CorpseKind kind;
    /** died on fire (or from fire): leaves a charred body */
    public boolean burning;

    public static DeathSnapshot of(LivingEntity mob, DamageSource source) {
        DeathSnapshot s = new DeathSnapshot();
        s.type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()));
        CompoundTag full = new CompoundTag();
        try {
            mob.saveWithoutId(full);
        } catch (Throwable ignored) {
            // a mob that can't be saved still gets a (default-looking) body
        }
        CompoundTag render = new CompoundTag();
        for (String key : full.getAllKeys()) {
            if (!STRIP.contains(key)) render.put(key, full.get(key));
        }
        s.renderTag = render;
        s.width = mob.getBbWidth();
        s.height = mob.getBbHeight();
        s.bodyYaw = mob.yBodyRot;
        s.feet = mob.position();
        Vec3 v = mob.getDeltaMovement();
        s.velocity = new Vec3(v.x, Math.max(-0.5, Math.min(0.4, v.y)), v.z);
        s.push = pushFrom(mob, source);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            s.equipment[slot.ordinal()] = mob.getItemBySlot(slot).copy();
        }
        s.arrows = StuckArrows.of(mob);
        s.name = mob.getCustomName();
        s.kind = CorpseKind.of(mob.getType());
        s.burning = mob.isOnFire() || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
        return s;
    }

    /** Direction and strength the killing blow throws the body. */
    private static Vec3 pushFrom(LivingEntity mob, DamageSource source) {
        Entity direct = source.getDirectEntity();
        Vec3 from = source.getSourcePosition();
        double strength = 0.18;
        Vec3 dir = null;
        if (direct != null && direct != mob && direct.getDeltaMovement().lengthSqr() > 0.01 && source.getEntity() != direct) {
            dir = direct.getDeltaMovement().normalize(); // projectile: the way it was flying
            strength = 0.22;
        } else if (from != null) {
            dir = mob.position().subtract(from);
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) strength = 0.55;
        }
        if (dir == null) return Vec3.ZERO;
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-6) return Vec3.ZERO;
        return flat.normalize().scale(strength).add(0, strength * 0.4, 0);
    }

    /** Keys in the mob's own data that hold gear drawn on it (saddles, horse armour, carpets). */
    public static final String[] EXTRA_GEAR_KEYS = {"SaddleItem", "ArmorItem", "DecorItem"};

    /** Moves gear the mob would have dropped onto the body instead (it can be taken off it later). */
    public boolean claimDrop(ItemStack dropped, boolean lootAll) {
        for (int i = 0; i < 6; i++) {
            if (lootable[i] || equipment[i].isEmpty()) continue;
            if (ItemStack.isSameItem(dropped, equipment[i])) {
                equipment[i] = dropped.copy();
                lootable[i] = true;
                return true;
            }
        }
        for (String key : EXTRA_GEAR_KEYS) {
            if (!renderTag.contains(key, Tag.TAG_COMPOUND) || extraLoot.contains(key)) continue;
            ItemStack worn = ItemStack.of(renderTag.getCompound(key));
            if (!worn.isEmpty() && ItemStack.isSameItem(dropped, worn)) {
                extraLoot.put(key, dropped.save(new CompoundTag()));
                return true;
            }
        }
        return false;
    }

    /** With lootAllEquipment on, anything still worn becomes lootable too. */
    public void lootEverything() {
        for (int i = 0; i < 6; i++) {
            if (!equipment[i].isEmpty()) lootable[i] = true;
        }
    }
}

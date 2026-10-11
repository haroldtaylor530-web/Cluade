package com.harold.realisticsurvival.item;

import com.harold.realisticsurvival.entity.ThrownSpear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * A spear: stab with it, or hold right-click to wind up and throw it. The longer the wind-up
 * (up to one second) the harder the throw. Pick it back up where it lands.
 */
public class SpearItem extends Item {
    private final float meleeDamage;
    private final float throwDamage;

    public SpearItem(Properties properties, float meleeDamage, float throwDamage) {
        super(properties);
        this.meleeDamage = meleeDamage;
        this.throwDamage = throwDamage;
    }

    public float throwDamage() {
        return throwDamage;
    }

    /** Melee: damage, and a slowish but longer-reaching thrust. */
    public static ItemAttributeModifiers attributes(float damage) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.9, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(com.harold.realisticsurvival.RealisticSurvival.id("spear_reach"), 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR; // vanilla's held-forward spear pose; the pull-back is in the item models
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.nextDamageWillBreak()) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;
        int held = getUseDuration(stack, entity) - timeLeft;
        if (held < 8) return false;
        float power = Math.min(1.0F, held / 20.0F);

        if (level instanceof ServerLevel serverLevel) {
            stack.hurtWithoutBreaking(1, player);
            ItemStack thrown = stack.copyWithCount(1);
            ThrownSpear spear = new ThrownSpear(serverLevel, player, thrown, throwDamage * (0.5F + 0.5F * power));
            spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F + 1.3F * power, 1.0F);
            if (player.getAbilities().instabuild) {
                spear.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            } else {
                stack.shrink(1);
            }
            serverLevel.addFreshEntity(spear);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.1F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }
}

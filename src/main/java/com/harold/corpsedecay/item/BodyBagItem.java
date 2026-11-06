package com.harold.corpsedecay.item;

import java.util.List;
import java.util.UUID;

import com.harold.corpsedecay.ModRegistry;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A body bag: right-click a corpse to put it in (up to five), right-click the ground to lay the
 * last one back down. Bodies don't decay while they're in the bag.
 */
public class BodyBagItem extends Item {
    public static final int MAX = 5;

    public BodyBagItem(Properties props) {
        super(props);
    }

    public static ListTag bodies(ItemStack bag) {
        return bag.hasTag() ? bag.getTag().getList("Bodies", Tag.TAG_COMPOUND) : new ListTag();
    }

    /** Puts a corpse in the bag. */
    public static boolean bag(ItemStack bag, CorpseEntity corpse, Player player) {
        ListTag list = bodies(bag);
        if (list.size() >= MAX) {
            player.displayClientMessage(Component.translatable("item.corpsedecay.body_bag.full"), true);
            return false;
        }
        if (corpse.holder() != -1) corpse.stopDrag(false);
        if (corpse.state() != CorpseEntity.FREE) corpse.unfix(true);
        CompoundTag tag = new CompoundTag();
        corpse.saveWithoutId(tag);
        tag.putLong("BaggedAt", corpse.level().getGameTime());
        list.add(tag);
        bag.getOrCreateTag().put("Bodies", list);
        corpse.level().playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1F, 0.7F);
        corpse.discard();
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack bag = ctx.getItemInHand();
        ListTag list = bodies(bag);
        if (list.isEmpty()) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        if (level instanceof ServerLevel sl) {
            CompoundTag tag = list.getCompound(list.size() - 1);
            CorpseEntity c = ModRegistry.CORPSE.get().create(sl);
            if (c == null) return InteractionResult.FAIL;
            c.load(tag);
            c.setUUID(UUID.randomUUID());
            // no time passed in the bag
            c.setDeathTime(c.deathTime() + (sl.getGameTime() - tag.getLong("BaggedAt")));
            Vec3 at = ctx.getClickLocation().add(Vec3.atLowerCornerOf(ctx.getClickedFace().getNormal()).scale(0.3));
            c.setPos(at.x, at.y + Math.max(c.bodyWidth(), c.bodyDepth()) / 2.0 + 0.3, at.z);
            c.layDown();
            sl.addFreshEntity(c);
            list.remove(list.size() - 1);
            bag.getOrCreateTag().put("Bodies", list);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1F, 0.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        ListTag list = bodies(stack);
        tip.add(Component.translatable("item.corpsedecay.body_bag.count", list.size(), MAX).withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Component name = t.contains("CustomName") ? Component.Serializer.fromJson(t.getString("CustomName"))
                    : EntityType.byString(t.getString("MobType")).map(EntityType::getDescription).orElse(Component.literal("?"));
            if (name != null) tip.add(Component.literal("  ").append(name).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (list.isEmpty()) tip.add(Component.translatable("item.corpsedecay.body_bag.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

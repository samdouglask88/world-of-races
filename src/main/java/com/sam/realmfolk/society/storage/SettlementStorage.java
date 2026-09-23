package com.sam.realmfolk.society.storage;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

public final class SettlementStorage {
    private final ServerLevel level;
    private final Settlement settlement;

    public SettlementStorage(ServerLevel level, Settlement settlement) {
        this.level = level;
        this.settlement = settlement;
    }

    public int count(Item item) { return count(stack -> stack.is(item)); }
    public int countFood() { return count(ItemStack::isEdible); }

    public int count(Predicate<ItemStack> predicate) {
        int total = 0;
        for (BlockPos position : settlement.storagePositions()) {
            Container container = container(position);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && predicate.test(stack)) total += stack.getCount();
            }
        }
        return total;
    }

    public Optional<BlockPos> nearest(BlockPos origin, Predicate<ItemStack> predicate) {
        return settlement.storagePositions().stream().filter(level::hasChunkAt)
                .filter(position -> contains(container(position), predicate))
                .min(Comparator.comparingDouble(position -> position.distSqr(origin)));
    }

    public Optional<BlockPos> nearestWithSpace(BlockPos origin, ItemStack stack) {
        return settlement.storagePositions().stream().filter(level::hasChunkAt)
                .filter(position -> canFit(container(position), stack))
                .min(Comparator.comparingDouble(position -> position.distSqr(origin)));
    }

    public ItemStack extract(Predicate<ItemStack> predicate, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack template = firstMatching(predicate);
        if (template.isEmpty() || count(stack -> ItemStack.isSameItemSameTags(template, stack)) < amount) {
            return ItemStack.EMPTY;
        }
        ItemStack result = template.copyWithCount(0);
        int remaining = amount;
        for (BlockPos position : settlement.storagePositions()) {
            Container container = container(position);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !ItemStack.isSameItemSameTags(template, stack)) continue;
                int moved = Math.min(remaining, stack.getCount());
                result.grow(moved);
                stack.shrink(moved);
                remaining -= moved;
                if (stack.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                container.setChanged();
            }
            if (remaining == 0) return result;
        }
        if (!result.isEmpty()) insert(result);
        return ItemStack.EMPTY;
    }

    private ItemStack firstMatching(Predicate<ItemStack> predicate) {
        for (BlockPos position : settlement.storagePositions()) {
            Container container = container(position);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && predicate.test(stack)) return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack extract(Item item, int amount) { return extract(stack -> stack.is(item), amount); }

    public boolean insert(ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        if (totalCapacity(incoming) < incoming.getCount()) return false;
        ItemStack remaining = incoming.copy();
        for (BlockPos position : settlement.storagePositions()) {
            Container container = container(position);
            if (container == null) continue;
            add(container, remaining);
            container.setChanged();
            if (remaining.isEmpty()) return true;
        }
        return remaining.isEmpty();
    }

    public boolean moveOneFoodTo(Container target) {
        ItemStack food = extract(ItemStack::isEdible, 1);
        if (food.isEmpty()) return false;
        if (add(target, food)) { target.setChanged(); return true; }
        insert(food);
        return false;
    }

    public boolean moveItemTo(Item item, int amount, Container target) {
        if (!canFit(target, new ItemStack(item, amount))) return false;
        ItemStack extracted = extract(item, amount);
        if (extracted.isEmpty()) return false;
        if (add(target, extracted)) { target.setChanged(); return true; }
        insert(extracted);
        return false;
    }

    public int depositExcess(Container source) {
        int moved = 0;
        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack.isEmpty() || stack.is(Items.EMERALD)) continue;
            ItemStack copy = stack.copy();
            if (!insert(copy)) continue;
            moved += stack.getCount();
            source.setItem(i, ItemStack.EMPTY);
        }
        if (moved > 0) source.setChanged();
        return moved;
    }

    public int depositExcess(ResidentEntity resident) {
        Container source = resident.getNpcInventory();
        int moved = 0;
        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (!isDepositable(resident, stack)) continue;
            ItemStack copy = stack.copy();
            if (!insert(copy)) continue;
            moved += stack.getCount();
            source.setItem(i, ItemStack.EMPTY);
        }
        if (moved > 0) source.setChanged();
        return moved;
    }

    public static boolean hasDepositable(ResidentEntity resident) {
        Container source = resident.getNpcInventory();
        for (int i = 0; i < source.getContainerSize(); i++) {
            if (isDepositable(resident, source.getItem(i))) return true;
        }
        return false;
    }

    public static boolean isDepositable(ResidentEntity resident, ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.EMERALD) || stack.isEdible()) return false;
        Item requiredTool = resident.getProfessionData().profession().requiredTool();
        return requiredTool == Items.AIR || !stack.is(requiredTool);
    }

    @Nullable
    public Container container(BlockPos position) {
        if (!level.hasChunkAt(position) || !level.getBlockState(position).is(Blocks.BARREL)) return null;
        return level.getBlockEntity(position) instanceof Container container ? container : null;
    }

    public static boolean canFit(@Nullable Container container, ItemStack incoming) {
        if (container == null || incoming.isEmpty()) return false;
        int remaining = incoming.getCount();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack current = container.getItem(i);
            if (current.isEmpty()) remaining -= Math.min(incoming.getMaxStackSize(), container.getMaxStackSize());
            else if (ItemStack.isSameItemSameTags(current, incoming)) remaining -= Math.max(0, Math.min(current.getMaxStackSize(), container.getMaxStackSize()) - current.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    public static boolean add(Container container, ItemStack remaining) {
        if (remaining.isEmpty()) return true;
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack current = container.getItem(i);
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, remaining)) {
                int moved = Math.min(remaining.getCount(), Math.min(current.getMaxStackSize(), container.getMaxStackSize()) - current.getCount());
                if (moved > 0) { current.grow(moved); remaining.shrink(moved); }
            }
        }
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            int moved = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), container.getMaxStackSize()));
            container.setItem(i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        return remaining.isEmpty();
    }

    private int totalCapacity(ItemStack incoming) {
        int capacity = 0;
        for (BlockPos position : settlement.storagePositions()) {
            Container container = container(position);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack current = container.getItem(i);
                if (current.isEmpty()) capacity += Math.min(incoming.getMaxStackSize(), container.getMaxStackSize());
                else if (ItemStack.isSameItemSameTags(current, incoming)) capacity += Math.max(0, Math.min(current.getMaxStackSize(), container.getMaxStackSize()) - current.getCount());
            }
        }
        return capacity;
    }

    private static boolean contains(@Nullable Container container, Predicate<ItemStack> predicate) {
        if (container == null) return false;
        for (int i = 0; i < container.getContainerSize(); i++) if (predicate.test(container.getItem(i))) return true;
        return false;
    }
}

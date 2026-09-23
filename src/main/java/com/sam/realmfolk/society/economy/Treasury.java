package com.sam.realmfolk.society.economy;

import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Treasury {
    private final ServerLevel level;
    private final Settlement settlement;

    public Treasury(ServerLevel level, Settlement settlement) {
        this.level = level;
        this.settlement = settlement;
    }

    public int balance() {
        Container container = container();
        if (container == null) return 0;
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.EMERALD)) total += container.getItem(i).getCount();
        return total;
    }

    public boolean depositFrom(Container source, int amount, EconomyTransactionType type,
                               long gameTime, java.util.UUID personId, String note) {
        Container treasury = container();
        if (treasury == null || amount <= 0 || count(source) < amount
                || !SettlementStorage.canFit(treasury, new ItemStack(Items.EMERALD, amount))) return false;
        remove(source, amount);
        ItemStack emeralds = new ItemStack(Items.EMERALD, amount);
        if (!SettlementStorage.add(treasury, emeralds)) {
            SettlementStorage.add(source, new ItemStack(Items.EMERALD, amount));
            return false;
        }
        source.setChanged();
        treasury.setChanged();
        settlement.economy().record(new EconomyTransaction(type, amount, gameTime, personId, note));
        return true;
    }

    public boolean withdrawTo(Container target, int amount, EconomyTransactionType type,
                              long gameTime, java.util.UUID personId, String note) {
        Container treasury = container();
        if (treasury == null || amount <= 0 || balance() < amount
                || !SettlementStorage.canFit(target, new ItemStack(Items.EMERALD, amount))) return false;
        remove(treasury, amount);
        ItemStack emeralds = new ItemStack(Items.EMERALD, amount);
        if (!SettlementStorage.add(target, emeralds)) {
            SettlementStorage.add(treasury, new ItemStack(Items.EMERALD, amount));
            return false;
        }
        treasury.setChanged();
        target.setChanged();
        settlement.economy().record(new EconomyTransaction(type, amount, gameTime, personId, note));
        return true;
    }

    private Container container() {
        BlockPos position = settlement.treasuryPosition();
        return position == null ? null : new SettlementStorage(level, settlement).container(position);
    }

    private static int count(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.EMERALD)) total += container.getItem(i).getCount();
        return total;
    }

    private static void remove(Container container, int amount) {
        for (int i = 0; i < container.getContainerSize() && amount > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(Items.EMERALD)) continue;
            int moved = Math.min(amount, stack.getCount());
            stack.shrink(moved);
            amount -= moved;
            if (stack.isEmpty()) container.setItem(i, ItemStack.EMPTY);
        }
    }
}

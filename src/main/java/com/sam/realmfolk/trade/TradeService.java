package com.sam.realmfolk.trade;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TradeService {
    private TradeService() {}

    public static Result buy(ServerPlayer player, ResidentEntity npc, int stockSlot, int amount) {
        if (!valid(player, npc, amount)) return Result.INVALID;
        Container stock = npc.getTradeInventory();
        if (stockSlot < 0 || stockSlot >= stock.getContainerSize()) return Result.INVALID;
        ItemStack product = stock.getItem(stockSlot);
        int unitPrice = TradePriceRegistry.get(product.getItem()).buyPrice();
        if (product.isEmpty() || product.is(Items.EMERALD) || unitPrice <= 0 || product.getCount() < amount) return Result.OUT_OF_STOCK;
        int total = safeTotal(unitPrice, amount);
        if (total < 1 || count(player.getInventory(), Items.EMERALD) < total) return Result.PLAYER_EMERALDS;
        ItemStack delivery = product.copyWithCount(amount);
        if (!canFit(player.getInventory(), delivery) || !canFit(stock, new ItemStack(Items.EMERALD, total))) return Result.NO_SPACE;
        remove(player.getInventory(), Items.EMERALD, total);
        add(stock, new ItemStack(Items.EMERALD, total));
        product.shrink(amount);
        if (product.isEmpty()) stock.setItem(stockSlot, ItemStack.EMPTY);
        add(player.getInventory(), delivery);
        player.getInventory().setChanged(); stock.setChanged();
        if (npc.getSettlementId() != null && npc.getPersonId() != null) {
            SettlementSavedData.get(player.serverLevel().getServer()).get(npc.getSettlementId()).ifPresent(settlement -> {
                settlement.economy().recordIncome(npc.getPersonId(), total);
                SettlementSavedData.get(player.serverLevel().getServer()).changed();
            });
        }
        return Result.SUCCESS;
    }

    public static Result sell(ServerPlayer player, ResidentEntity npc, int playerSlot, int amount) {
        if (!valid(player, npc, amount) || playerSlot < 0 || playerSlot >= 36) return Result.INVALID;
        Inventory inventory = player.getInventory();
        ItemStack offered = inventory.getItem(playerSlot);
        int unitPrice = TradePriceRegistry.get(offered.getItem()).sellPrice();
        if (offered.isEmpty() || offered.is(Items.EMERALD) || unitPrice <= 0 || offered.getCount() < amount) return Result.PLAYER_ITEMS;
        int total = safeTotal(unitPrice, amount);
        Container stock = npc.getTradeInventory();
        if (total < 1 || count(stock, Items.EMERALD) < total) return Result.NPC_EMERALDS;
        ItemStack goods = offered.copyWithCount(amount);
        if (!canFit(stock, goods) || !canFit(inventory, new ItemStack(Items.EMERALD, total))) return Result.NO_SPACE;
        offered.shrink(amount);
        if (offered.isEmpty()) inventory.setItem(playerSlot, ItemStack.EMPTY);
        add(stock, goods);
        remove(stock, Items.EMERALD, total);
        add(inventory, new ItemStack(Items.EMERALD, total));
        inventory.setChanged(); stock.setChanged();
        return Result.SUCCESS;
    }

    private static boolean valid(ServerPlayer player, ResidentEntity npc, int amount) {
        return amount > 0 && amount <= 64 && npc.isAlive() && npc.isWorkingAge()
                && npc.distanceToSqr(player) <= 64;
    }
    private static int safeTotal(int price, int amount) {
        long total = (long) price * amount; return total > Integer.MAX_VALUE ? -1 : (int) total;
    }
    public static int count(Container container, net.minecraft.world.item.Item item) {
        int count = 0; for (int i=0;i<container.getContainerSize();i++) if(container.getItem(i).is(item)) count += container.getItem(i).getCount(); return count;
    }
    private static boolean canFit(Container container, ItemStack incoming) {
        int remaining = incoming.getCount();
        for (int i=0;i<container.getContainerSize();i++) {
            ItemStack current=container.getItem(i);
            if(current.isEmpty()) remaining -= incoming.getMaxStackSize();
            else if(ItemStack.isSameItemSameTags(current,incoming)) remaining -= Math.max(0,current.getMaxStackSize()-current.getCount());
            if(remaining<=0) return true;
        }
        return false;
    }
    private static void remove(Container container, net.minecraft.world.item.Item item, int amount) {
        for(int i=0;i<container.getContainerSize()&&amount>0;i++) if(container.getItem(i).is(item)) {
            ItemStack stack=container.getItem(i); int take=Math.min(amount,stack.getCount()); stack.shrink(take); amount-=take;
            if(stack.isEmpty()) container.setItem(i,ItemStack.EMPTY);
        }
    }
    private static void add(Container container, ItemStack incoming) {
        ItemStack remaining=incoming.copy();
        for(int i=0;i<container.getContainerSize()&&!remaining.isEmpty();i++) {
            ItemStack current=container.getItem(i);
            if(!current.isEmpty()&&ItemStack.isSameItemSameTags(current,remaining)) {
                int move=Math.min(remaining.getCount(),current.getMaxStackSize()-current.getCount()); current.grow(move); remaining.shrink(move);
            }
        }
        for(int i=0;i<container.getContainerSize()&&!remaining.isEmpty();i++) if(container.getItem(i).isEmpty()) {
            int move=Math.min(remaining.getCount(),remaining.getMaxStackSize()); container.setItem(i,remaining.copyWithCount(move)); remaining.shrink(move);
        }
        if(!remaining.isEmpty()) throw new IllegalStateException("Validacao de espaco da troca divergiu");
    }
    public enum Result { SUCCESS, INVALID, OUT_OF_STOCK, PLAYER_EMERALDS, PLAYER_ITEMS, NPC_EMERALDS, NO_SPACE }
}

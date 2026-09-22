package com.sam.realmfolk.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TradePriceRegistry {
    private static final Map<Item, Price> PRICES = new IdentityHashMap<>();
    static {
        add(Items.BREAD, 2, 1); add(Items.WHEAT, 1, 1); add(Items.COOKED_BEEF, 3, 1);
        add(Items.IRON_INGOT, 4, 2); add(Items.COAL, 2, 1); add(Items.ARROW, 1, 1);
        add(Items.IRON_SWORD, 12, 6); add(Items.LEATHER_CHESTPLATE, 10, 5);
    }
    private TradePriceRegistry() {}
    private static void add(Item item, int buy, int sell) { PRICES.put(item, new Price(buy, sell)); }
    public static Price get(Item item) { return PRICES.getOrDefault(item, Price.UNTRADEABLE); }
    public record Price(int buyPrice, int sellPrice) {
        public static final Price UNTRADEABLE = new Price(0, 0);
    }
}

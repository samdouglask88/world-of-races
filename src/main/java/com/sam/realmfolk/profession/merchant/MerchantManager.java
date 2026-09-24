package com.sam.realmfolk.profession.merchant;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import com.sam.realmfolk.trade.TradePriceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.UUID;

/** Moves real, tradeable goods from settlement barrels into a merchant's persistent stock. */
public final class MerchantManager {
    public static final int MAX_BATCH = 8;
    public static final int MAX_OPEN_ORDERS = 1;

    private MerchantManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        ResidentEntity merchant = readyMerchant(level, settlement);
        if (merchant == null) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        for (Item item : TradePriceRegistry.tradableItems()) {
            int available = Math.max(0, storage.count(item) - settlement.reservedAmount(item, level.getGameTime()));
            if (available <= 0 || !SettlementStorage.canFit(merchant.getTradeInventory(), new ItemStack(item, 1))) continue;
            BlockPos source = storage.nearest(merchant.blockPosition(), stack -> stack.is(item)).orElse(null);
            if (source == null) continue;
            int amount = Math.min(MAX_BATCH, available);
            WorkOrder order = createOrder(level, settlement, source, item, amount);
            if (order != null) return true;
        }
        return false;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity merchant, WorkOrder order) {
        if (!isStockOrder(order) || merchant.getProfessionData().profession() != NpcProfession.MERCHANT
                || !ProfessionService.validateOrFindStation(level, merchant)) return false;
        BlockPos source = order.targetPosition();
        if (source == null || !settlement.storagePositions().contains(source)
                || !level.hasChunkAt(source) || !level.getBlockState(source).is(Blocks.BARREL)) return false;
        int available = Math.max(0, new SettlementStorage(level, settlement).count(order.requirement())
                - settlement.reservedAmount(order.requirement(), level.getGameTime()));
        return available >= order.requiredAmount()
                && SettlementStorage.canFit(merchant.getTradeInventory(),
                new ItemStack(order.requirement(), order.requiredAmount()));
    }

    public static boolean reserveStock(ServerLevel level, Settlement settlement, WorkOrder order) {
        if (!isStockOrder(order)) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        int available = Math.max(0, storage.count(order.requirement())
                - settlement.reservedAmount(order.requirement(), level.getGameTime()));
        return available >= order.requiredAmount() && settlement.reserveStorage(order.id(), order.requirement(),
                order.requiredAmount(), level.getGameTime() + WorkOrder.DEFAULT_RESERVATION_TICKS);
    }

    public static WorkResult restock(ServerLevel level, Settlement settlement,
                                     ResidentEntity merchant, WorkOrder order) {
        BlockPos source = order.targetPosition();
        if (source == null || !settlement.storagePositions().contains(source)) return WorkResult.INVALID_SOURCE;
        if (!level.hasChunkAt(source)) return WorkResult.UNLOADED;
        if (!level.getBlockState(source).is(Blocks.BARREL)
                || merchant.getProfessionData().profession() != NpcProfession.MERCHANT) return WorkResult.INVALID_SOURCE;
        ItemStack requested = new ItemStack(order.requirement(), order.requiredAmount());
        if (!SettlementStorage.canFit(merchant.getTradeInventory(), requested)) return WorkResult.INVENTORY_FULL;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        ItemStack extracted = storage.extract(order.requirement(), order.requiredAmount());
        if (extracted.isEmpty()) return WorkResult.MISSING_STOCK;
        if (!SettlementStorage.add(merchant.getTradeInventory(), extracted)) {
            storage.insert(extracted);
            return WorkResult.INVENTORY_FULL;
        }
        merchant.getTradeInventory().setChanged();
        merchant.getProfessionData().addExperience(10);
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static WorkOrder createOrder(ServerLevel level, Settlement settlement,
                                        BlockPos source, Item item, int amount) {
        if (amount <= 0 || !settlement.storagePositions().contains(source)
                || !level.hasChunkAt(source) || !level.getBlockState(source).is(Blocks.BARREL)
                || TradePriceRegistry.get(item).buyPrice() <= 0 || hasOpenFor(settlement, item)) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.DELIVER_RESOURCE,
                Math.max(55, settlement.government().priority(SettlementPolicy.WEALTH)), settlement.id(),
                source, level.dimension(), item, amount, item, amount,
                now, now + DailyEconomyManager.DAY_TICKS);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean isStockOrder(WorkOrder order) {
        return order.type() == WorkOrderType.DELIVER_RESOURCE && order.targetPosition() != null
                && order.requirement() == order.result() && order.requirement() != net.minecraft.world.item.Items.AIR
                && order.requiredAmount() > 0 && order.resultAmount() == order.requiredAmount()
                && TradePriceRegistry.get(order.requirement()).buyPrice() > 0;
    }

    @Nullable
    private static ResidentEntity readyMerchant(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.MERCHANT
                    && ProfessionService.validateOrFindStation(level, resident)) return resident;
        }
        return null;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isStockOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOpenFor(Settlement settlement, Item item) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isStockOrder(order) && order.requirement() == item
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    public enum WorkResult { COMPLETED, INVENTORY_FULL, MISSING_STOCK, UNLOADED, INVALID_SOURCE }
}

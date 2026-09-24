package com.sam.realmfolk.profession.blacksmith;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.UUID;

/** Creates blacksmith orders from actual tool shortages in the settlement. */
public final class BlacksmithManager {
    public static final int MAX_OPEN_ORDERS = 2;

    private BlacksmithManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        ResidentEntity blacksmith = readyBlacksmith(level, settlement);
        if (blacksmith == null) return false;
        Item requiredTool = missingCraftableTool(level, settlement);
        if (requiredTool == Items.AIR || hasOpenFor(settlement, requiredTool)) return false;
        BlacksmithRecipe recipe = BlacksmithRecipe.forResult(requiredTool).orElse(null);
        if (recipe == null || recipe.level > blacksmith.getProfessionData().level()
                || !ingredientsAvailable(level, settlement, blacksmith, recipe)) return false;
        long now = level.getGameTime();
        BlacksmithRecipe.Ingredient primary = recipe.ingredients.get(0);
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(75, settlement.government().priority(SettlementPolicy.PRODUCTION)),
                settlement.id(), null, level.dimension(), primary.item(), primary.count(),
                recipe.result, 1, now, now + DailyEconomyManager.DAY_TICKS * 2L);
        return settlement.taskBoard().add(order);
    }

    public static boolean isBlacksmithOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE
                && BlacksmithRecipe.forResult(order.result()).isPresent();
    }

    private static Item missingCraftableTool(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        SettlementStorage storage = new SettlementStorage(level, settlement);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (!(entity instanceof ResidentEntity resident) || !resident.isAlive() || !resident.isWorkingAge()) continue;
            Item tool = resident.getProfessionData().profession().requiredTool();
            if (tool != Items.AIR && !ProfessionService.hasRequiredTool(resident)
                    && storage.count(tool) == 0 && BlacksmithRecipe.forResult(tool).isPresent()) return tool;
        }
        return Items.AIR;
    }

    private static boolean ingredientsAvailable(ServerLevel level, Settlement settlement,
                                                ResidentEntity blacksmith, BlacksmithRecipe recipe) {
        SettlementStorage storage = new SettlementStorage(level, settlement);
        for (BlacksmithRecipe.Ingredient ingredient : recipe.ingredients) {
            int available = count(blacksmith.getNpcInventory(), ingredient.item())
                    + Math.max(0, storage.count(ingredient.item())
                    - settlement.reservedAmount(ingredient.item(), level.getGameTime()));
            if (available < ingredient.count()) return false;
        }
        return true;
    }

    @Nullable
    private static ResidentEntity readyBlacksmith(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.BLACKSMITH
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)) return resident;
        }
        return null;
    }

    private static boolean hasOpenFor(Settlement settlement, Item result) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isBlacksmithOrder(order) && order.result() == result
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isBlacksmithOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount();
        }
        return count;
    }
}

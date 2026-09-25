package com.sam.realmfolk.profession.cooking;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Physical smoker recipes using ingredients carried from settlement storage. */
public final class CookingManager {
    public static final int WORK_INTERVAL_TICKS = 40;
    public static final int WHEAT_PER_BREAD = 3;
    public static final int MAX_OPEN_ORDERS = 2;
    private static final List<KitchenRecipe> RECIPES = List.of(
            new KitchenRecipe(Items.WHEAT, WHEAT_PER_BREAD, Items.BREAD, 1, 12),
            new KitchenRecipe(Items.POTATO, 1, Items.BAKED_POTATO, 1, 8),
            new KitchenRecipe(Items.BEEF, 1, Items.COOKED_BEEF, 1, 10),
            new KitchenRecipe(Items.PORKCHOP, 1, Items.COOKED_PORKCHOP, 1, 10),
            new KitchenRecipe(Items.CHICKEN, 1, Items.COOKED_CHICKEN, 1, 9),
            new KitchenRecipe(Items.MUTTON, 1, Items.COOKED_MUTTON, 1, 9),
            new KitchenRecipe(Items.RABBIT, 1, Items.COOKED_RABBIT, 1, 9),
            new KitchenRecipe(Items.COD, 1, Items.COOKED_COD, 1, 8),
            new KitchenRecipe(Items.SALMON, 1, Items.COOKED_SALMON, 1, 8));

    private CookingManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        int foodTarget = Math.max(16, settlement.memberIds().size() * 6);
        if (storage.countFood() >= foodTarget) return false;
        ResidentEntity cook = readyCook(level, settlement);
        if (cook == null || cook.getProfessionData().workstation() == null) return false;
        for (KitchenRecipe recipe : RECIPES) {
            int available = available(level, settlement, storage, recipe.ingredient());
            if (available >= recipe.ingredientCount()
                    && createOrderAt(level, settlement, cook.getProfessionData().workstation(), recipe) != null) return true;
        }
        return false;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity resident, WorkOrder order) {
        BlockPos station = order.targetPosition();
        SettlementStorage storage = new SettlementStorage(level, settlement);
        KitchenRecipe recipe = recipeFor(order);
        return isCookingOrder(order)
                && recipe != null
                && resident.getProfessionData().profession() == NpcProfession.COOK
                && station != null && settlement.contains(station) && level.hasChunkAt(station)
                && level.getBlockState(station).is(Blocks.SMOKER)
                && station.equals(resident.getProfessionData().workstation())
                && ProfessionService.validateOrFindStation(level, resident)
                && ProfessionService.hasRequiredTool(resident)
                && count(resident.getNpcInventory(), recipe.ingredient())
                + available(level, settlement, storage, recipe.ingredient()) >= recipe.ingredientCount();
    }

    public static WorkResult prepareMeal(ServerLevel level, Settlement settlement,
                                         ResidentEntity resident, WorkOrder order) {
        BlockPos station = order.targetPosition();
        KitchenRecipe recipe = recipeFor(order);
        if (recipe == null) return WorkResult.INVALID_STATION;
        if (station == null || !settlement.contains(station)
                || !station.equals(resident.getProfessionData().workstation())) return WorkResult.INVALID_STATION;
        if (!level.hasChunkAt(station)) return WorkResult.UNLOADED;
        if (!level.getBlockState(station).is(Blocks.SMOKER)) return WorkResult.INVALID_STATION;
        if (!ProfessionService.hasRequiredTool(resident)) return WorkResult.NO_TOOL;
        ItemStack result = new ItemStack(recipe.result(), recipe.resultCount());
        if (!canFit(resident.getNpcInventory(), result)) return WorkResult.INVENTORY_FULL;

        if (count(resident.getNpcInventory(), recipe.ingredient()) < recipe.ingredientCount()) {
            return WorkResult.MISSING_INGREDIENTS;
        }
        remove(resident.getNpcInventory(), recipe.ingredient(), recipe.ingredientCount());
        if (!SettlementStorage.add(resident.getNpcInventory(), result)) {
            SettlementStorage.add(resident.getNpcInventory(),
                    new ItemStack(recipe.ingredient(), recipe.ingredientCount()));
            return WorkResult.INVENTORY_FULL;
        }
        resident.getNpcInventory().setChanged();
        ProfessionService.damageRequiredTool(resident);
        resident.getProfessionData().addExperience(recipe.experience());
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static WorkOrder createOrderAt(ServerLevel level, Settlement settlement, BlockPos station) {
        return createOrderAt(level, settlement, station, RECIPES.get(0));
    }

    @Nullable
    public static WorkOrder createOrderFor(ServerLevel level, Settlement settlement,
                                           BlockPos station, Item ingredient) {
        for (KitchenRecipe recipe : RECIPES) {
            if (recipe.ingredient() == ingredient) return createOrderAt(level, settlement, station, recipe);
        }
        return null;
    }

    @Nullable
    private static WorkOrder createOrderAt(ServerLevel level, Settlement settlement,
                                           BlockPos station, KitchenRecipe recipe) {
        SettlementStorage storage = new SettlementStorage(level, settlement);
        if (!settlement.contains(station) || !level.hasChunkAt(station)
                || !level.getBlockState(station).is(Blocks.SMOKER)
                || hasOrderFor(settlement, station)
                || available(level, settlement, storage, recipe.ingredient()) < recipe.ingredientCount()) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(75, settlement.government().priority(
                        com.sam.realmfolk.society.government.SettlementPolicy.FOOD)),
                settlement.id(), station, level.dimension(), recipe.ingredient(), recipe.ingredientCount(),
                recipe.result(), recipe.resultCount(), now, now + DailyEconomyManager.DAY_TICKS);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean reserveIngredients(ServerLevel level, Settlement settlement,
                                             ResidentEntity resident, WorkOrder order) {
        if (!isCookingOrder(order)) return false;
        int needed = missingIngredients(resident, order);
        if (needed == 0) return true;
        KitchenRecipe recipe = recipeFor(order);
        if (recipe == null) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        if (available(level, settlement, storage, recipe.ingredient()) < needed) return false;
        return settlement.reserveStorage(order.id(), recipe.ingredient(), needed,
                level.getGameTime() + WorkOrder.DEFAULT_RESERVATION_TICKS);
    }

    public static int missingIngredients(ResidentEntity resident, WorkOrder order) {
        if (!isCookingOrder(order)) return 0;
        KitchenRecipe recipe = recipeFor(order);
        return recipe == null ? 0 : Math.max(0, recipe.ingredientCount()
                - count(resident.getNpcInventory(), recipe.ingredient()));
    }

    public static boolean collectIngredients(ServerLevel level, Settlement settlement,
                                             ResidentEntity resident, WorkOrder order) {
        int needed = missingIngredients(resident, order);
        KitchenRecipe recipe = recipeFor(order);
        return recipe != null && (needed == 0 || new SettlementStorage(level, settlement)
                .moveItemTo(recipe.ingredient(), needed, resident.getNpcInventory()));
    }

    public static boolean isCookingOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE && order.targetPosition() != null
                && recipeFor(order) != null;
    }

    @Nullable
    public static Item missingIngredient(WorkOrder order) {
        KitchenRecipe recipe = recipeFor(order);
        return recipe == null ? null : recipe.ingredient();
    }

    private static int available(ServerLevel level, Settlement settlement,
                                 SettlementStorage storage, Item item) {
        return Math.max(0, storage.count(item)
                - settlement.reservedAmount(item, level.getGameTime()));
    }

    @Nullable
    private static KitchenRecipe recipeFor(WorkOrder order) {
        for (KitchenRecipe recipe : RECIPES) {
            if (order.requirement() == recipe.ingredient()
                    && order.requiredAmount() == recipe.ingredientCount()
                    && order.result() == recipe.result()
                    && order.resultAmount() == recipe.resultCount()) return recipe;
        }
        return null;
    }

    @Nullable
    private static ResidentEntity readyCook(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.COOK
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)) return resident;
        }
        return null;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isCookingOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOrderFor(Settlement settlement, BlockPos station) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isCookingOrder(order) && station.equals(order.targetPosition())
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    private static boolean canFit(SimpleContainer inventory, ItemStack incoming) {
        SimpleContainer simulation = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) simulation.setItem(i, inventory.getItem(i).copy());
        return simulation.addItem(incoming.copy()).isEmpty();
    }

    private static int count(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) total += container.getItem(slot).getCount();
        }
        return total;
    }

    private static void remove(Container container, net.minecraft.world.item.Item item, int amount) {
        for (int slot = 0; slot < container.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(amount, stack.getCount());
            stack.shrink(removed);
            amount -= removed;
            if (stack.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
        }
        container.setChanged();
    }

    public enum WorkResult {
        COMPLETED, NO_TOOL, INVENTORY_FULL, MISSING_INGREDIENTS, UNLOADED, INVALID_STATION
    }

    private record KitchenRecipe(Item ingredient, int ingredientCount, Item result,
                                 int resultCount, int experience) {}
}

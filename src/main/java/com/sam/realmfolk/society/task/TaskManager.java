package com.sam.realmfolk.society.task;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.profession.blacksmith.BlacksmithRecipe;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

public final class TaskManager {
    private TaskManager() {}

    public static Optional<WorkOrder> reserveBest(ServerLevel level, Settlement settlement, ResidentEntity resident) {
        if (resident.getPersonId() == null || !resident.isWorkingAge()
                || resident.getBirthHomePosition() != null || resident.getNeeds().hungerEmergency()) return Optional.empty();
        WorkOrder chosen = null;
        int bestScore = Integer.MIN_VALUE;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (order.status() != WorkOrderStatus.AVAILABLE || !order.dimension().equals(level.dimension())
                    || !canComplete(level, settlement, resident, order)) continue;
            int score = score(level, settlement, resident, order);
            if (chosen == null || score > bestScore) {
                chosen = order;
                bestScore = score;
            }
        }
        if (chosen == null || !reserveResources(level, settlement, resident, chosen)) return Optional.empty();
        if (!chosen.reserve(resident.getPersonId(), level.getGameTime())) {
            settlement.releaseStorageReservations(chosen.id());
            return Optional.empty();
        }
        resident.setCurrentWorkOrderId(chosen.id());
        SettlementSavedData.get(level.getServer()).changed();
        return Optional.of(chosen);
    }

    public static boolean canComplete(ServerLevel level, Settlement settlement, ResidentEntity resident, WorkOrder order) {
        if (!resident.isWorkingAge() || resident.getBirthHomePosition() != null) return false;
        NpcProfession profession = resident.getProfessionData().profession();
        SettlementStorage storage = new SettlementStorage(level, settlement);
        return switch (order.type()) {
            case PRODUCE_RESOURCE -> canProduce(level, settlement, storage, resident, order);
            case BUILD, REPAIR -> isAdult(level, resident)
                    && (profession == NpcProfession.BUILDER || profession == NpcProfession.NONE);
            case GUARD -> profession == NpcProfession.GUARD;
            case FETCH_FOOD -> storage.countFood() > 0;
            case FETCH_TOOL -> !ProfessionService.hasRequiredTool(resident)
                    && profession.requiredTool() != Items.AIR && storage.count(profession.requiredTool()) > 0;
            case FETCH_RESOURCE -> order.requirement() != Items.AIR
                    && available(settlement, storage, order.requirement(), level.getGameTime()) >= order.requiredAmount();
            default -> true;
        };
    }

    public static int score(ServerLevel level, Settlement settlement, ResidentEntity resident, WorkOrder order) {
        int compatibility = 30;
        int distance = 0;
        if (order.targetPosition() != null) {
            distance = Math.min(40, (int) Math.sqrt(resident.blockPosition().distSqr(order.targetPosition())));
        }
        return order.priority() + compatibility - distance;
    }

    public static void releaseCurrent(Settlement settlement, ResidentEntity resident) {
        if (resident.getCurrentWorkOrderId() == null) return;
        settlement.taskBoard().get(resident.getCurrentWorkOrderId()).ifPresent(WorkOrder::release);
        settlement.releaseStorageReservations(resident.getCurrentWorkOrderId());
        resident.setCurrentWorkOrderId(null);
    }

    private static boolean canProduce(ServerLevel level, Settlement settlement, SettlementStorage storage,
                                      ResidentEntity resident, WorkOrder order) {
        if (resident.getProfessionData().profession() != NpcProfession.BLACKSMITH
                || resident.getProfessionData().workstation() == null
                || !level.hasChunkAt(resident.getProfessionData().workstation())
                || !ProfessionService.hasRequiredTool(resident)) return false;
        BlacksmithRecipe recipe = BlacksmithRecipe.forResult(order.result()).orElse(null);
        if (recipe == null || recipe.level > resident.getProfessionData().level()) return false;
        for (BlacksmithRecipe.Ingredient ingredient : recipe.ingredients) {
            int owned = count(resident.getNpcInventory(), ingredient.item());
            int stored = available(settlement, storage, ingredient.item(), level.getGameTime());
            if (owned + stored < ingredient.count()) return false;
        }
        return true;
    }

    private static boolean reserveResources(ServerLevel level, Settlement settlement,
                                            ResidentEntity resident, WorkOrder order) {
        if (order.type() != WorkOrderType.PRODUCE_RESOURCE) return true;
        BlacksmithRecipe recipe = BlacksmithRecipe.forResult(order.result()).orElse(null);
        if (recipe == null) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        for (BlacksmithRecipe.Ingredient ingredient : recipe.ingredients) {
            int needed = Math.max(0, ingredient.count() - count(resident.getNpcInventory(), ingredient.item()));
            if (needed == 0) continue;
            if (available(settlement, storage, ingredient.item(), level.getGameTime()) < needed) {
                settlement.releaseStorageReservations(order.id());
                return false;
            }
            settlement.reserveStorage(order.id(), ingredient.item(), needed,
                    level.getGameTime() + WorkOrder.DEFAULT_RESERVATION_TICKS);
        }
        return true;
    }

    private static int available(Settlement settlement, SettlementStorage storage, Item item, long now) {
        return Math.max(0, storage.count(item) - settlement.reservedAmount(item, now));
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) total += container.getItem(i).getCount();
        }
        return total;
    }

    private static boolean isAdult(ServerLevel level, ResidentEntity resident) {
        if (resident.getPersonId() == null) return false;
        return HumanSocietySavedData.get(level).getPerson(resident.getPersonId())
                .map(person -> person.getLifeStage() == LifeStage.ADULT || person.getLifeStage() == LifeStage.ELDER)
                .orElse(false);
    }
}

package com.sam.realmfolk.society.ai;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.integration.HostileMobIntegration;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.profession.blacksmith.BlacksmithRecipe;
import com.sam.realmfolk.society.needs.NeedsManager;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.construction.BlueprintLoader;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public final class WorkOrderGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQR = 6.25D;
    private final ResidentEntity resident;
    @Nullable private BlockPos target;
    private int actionTicks;

    public WorkOrderGoal(ResidentEntity resident) {
        this.resident = resident;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(resident.level() instanceof ServerLevel) || !resident.isAlive()) return false;
        if (resident.getBirthHomePosition() != null && resident.getNpcBrain().currentAction() != NpcAction.FLEE) return false;
        return switch (resident.getNpcBrain().currentAction()) {
            case FLEE, EAT, FETCH_FOOD, EXECUTE_WORK_ORDER, DEPOSIT_ITEMS, FETCH_TOOL -> true;
            default -> false;
        };
    }

    @Override
    public boolean canContinueToUse() { return canUse() && actionTicks < 400; }

    @Override
    public void start() {
        actionTicks = 0;
        target = null;
        if (!(resident.level() instanceof ServerLevel level)) return;
        Settlement settlement = settlement(level);
        switch (resident.getNpcBrain().currentAction()) {
            case EAT -> {
                NeedsManager.eatFromInventory(resident);
                resident.getNpcBrain().clearAction("Alimentado");
            }
            case FETCH_FOOD -> target = settlement == null ? null : new SettlementStorage(level, settlement)
                    .nearest(resident.blockPosition(), ItemStack::isEdible).orElse(null);
            case FETCH_TOOL -> {
                if (settlement != null) target = new SettlementStorage(level, settlement)
                        .nearest(resident.blockPosition(), stack -> stack.is(resident.getProfessionData().profession().requiredTool())).orElse(null);
            }
            case DEPOSIT_ITEMS -> {
                if (settlement != null) {
                    ItemStack sample = firstDepositable();
                    if (!sample.isEmpty()) target = new SettlementStorage(level, settlement)
                            .nearestWithSpace(resident.blockPosition(), sample).orElse(null);
                }
            }
            case EXECUTE_WORK_ORDER -> target = order(settlement) == null ? null : orderTarget(settlement, order(settlement));
            case FLEE -> target = fleeTarget(level);
            default -> { }
        }
        if (target == null && resident.getNpcBrain().currentAction() != NpcAction.EAT) resident.getNpcBrain().clearAction("Destino indisponível");
    }

    @Override
    public void tick() {
        actionTicks++;
        if (!(resident.level() instanceof ServerLevel level) || target == null) return;
        if (resident.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > ARRIVAL_DISTANCE_SQR) {
            resident.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
            return;
        }
        resident.getNavigation().stop();
        resident.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (actionTicks % 10 == 0) resident.swing(InteractionHand.MAIN_HAND);
        if (actionTicks < 30) return;

        Settlement settlement = settlement(level);
        if (settlement == null) { resident.getNpcBrain().clearAction("Povoado indisponível"); return; }
        SettlementStorage storage = new SettlementStorage(level, settlement);
        switch (resident.getNpcBrain().currentAction()) {
            case FETCH_FOOD -> {
                if (storage.moveOneFoodTo(resident.getNpcInventory())) {
                    NeedsManager.eatFromInventory(resident);
                    resident.getNpcBrain().clearAction("Comida retirada do armazém e consumida");
                } else resident.getNpcBrain().clearAction("Comida indisponível");
            }
            case FETCH_TOOL -> {
                var tool = resident.getProfessionData().profession().requiredTool();
                resident.getNpcBrain().clearAction(storage.moveItemTo(tool, 1, resident.getNpcInventory())
                        ? "Ferramenta retirada do armazém" : "Ferramenta indisponível");
            }
            case DEPOSIT_ITEMS -> resident.getNpcBrain().clearAction(
                    storage.depositExcess(resident) > 0 ? "Excedentes depositados" : "Nada para depositar");
            case EXECUTE_WORK_ORDER -> executeOrder(level, settlement, storage);
            case FLEE -> resident.getNpcBrain().clearAction("Afastou-se do perigo");
            default -> { }
        }
    }

    private void executeOrder(ServerLevel level, Settlement settlement, SettlementStorage storage) {
        WorkOrder order = order(settlement);
        if (order == null || resident.getPersonId() == null || !order.begin(resident.getPersonId(), level.getGameTime())) {
            resident.setCurrentWorkOrderId(null);
            resident.getNpcBrain().clearAction("Reserva de tarefa perdida");
            return;
        }
        boolean success = false;
        switch (order.type()) {
            case FETCH_FOOD -> success = storage.moveOneFoodTo(resident.getNpcInventory());
            case FETCH_TOOL -> {
                var item = order.requirement() == Items.AIR
                        ? resident.getProfessionData().profession().requiredTool() : order.requirement();
                success = item != Items.AIR && storage.moveItemTo(item,
                        Math.max(1, order.requiredAmount()), resident.getNpcInventory());
            }
            case FETCH_RESOURCE -> success = order.requirement() != Items.AIR
                    && storage.moveItemTo(order.requirement(), Math.max(1, order.requiredAmount()), resident.getNpcInventory());
            case DEPOSIT_RESOURCE, DELIVER_RESOURCE -> success = storage.depositExcess(resident) > 0;
            case PRODUCE_RESOURCE -> {
                BlacksmithRecipe.Ingredient missing = missingProductionIngredient(order);
                if (missing != null) {
                    int amount = missing.count() - count(resident.getNpcInventory(), missing.item());
                    if (amount > 0 && storage.moveItemTo(missing.item(), amount, resident.getNpcInventory())) {
                        order.heartbeat(resident.getPersonId(), level.getGameTime());
                        resident.getNpcBrain().clearAction("Material retirado do armazém");
                        SettlementSavedData.get(level.getServer()).changed();
                        return;
                    }
                    order.block("Material reservado não está mais disponível");
                    finish(settlement, false, order.blockedReason());
                    return;
                }
                ProfessionService.ProductionResult result = ProfessionService.produce(resident, order.result());
                success = result == ProfessionService.ProductionResult.SUCCESS || result == ProfessionService.ProductionResult.LEVEL_UP;
            }
            case GUARD -> success = true;
            case BUILD, REPAIR -> {
                ConstructionManager.WorkResult result = ConstructionManager.workProject(level, settlement, resident);
                switch (result) {
                    case COMPLETED -> { order.complete(); finish(settlement, true, "Construção concluída"); }
                    case IN_PROGRESS -> {
                        order.heartbeat(resident.getPersonId(), level.getGameTime());
                        resident.getNpcBrain().clearAction("Etapa da construção concluída");
                        SettlementSavedData.get(level.getServer()).changed();
                    }
                    case UNLOADED -> resident.getNpcBrain().clearAction("Chunk do projeto descarregado");
                    case WAITING_RESOURCES -> {
                        order.cancel();
                        finish(settlement, false, "Construção aguardando materiais");
                    }
                    case BLOCKED -> {
                        order.block("Local da construção bloqueado");
                        finish(settlement, false, "Construção bloqueada");
                    }
                }
                return;
            }
        }
        if (success) order.complete();
        else order.block("Recursos, ferramenta ou receita indisponíveis");
        finish(settlement, success, success ? "Tarefa concluída" : order.blockedReason());
    }

    private void finish(Settlement settlement, boolean success, String reason) {
        if (resident.getCurrentWorkOrderId() != null) {
            settlement.releaseStorageReservations(resident.getCurrentWorkOrderId());
        }
        resident.setCurrentWorkOrderId(null);
        resident.getNpcBrain().clearAction(reason);
        SettlementSavedData.get(((ServerLevel) resident.level()).getServer()).changed();
    }

    @Nullable
    private Settlement settlement(ServerLevel level) {
        return resident.getSettlementId() == null ? null
                : SettlementSavedData.get(level.getServer()).get(resident.getSettlementId()).orElse(null);
    }

    @Nullable
    private WorkOrder order(@Nullable Settlement settlement) {
        return settlement == null || resident.getCurrentWorkOrderId() == null ? null
                : settlement.taskBoard().get(resident.getCurrentWorkOrderId()).orElse(null);
    }

    @Nullable
    private BlockPos orderTarget(Settlement settlement, WorkOrder order) {
        if (order.type() == WorkOrderType.BUILD || order.type() == WorkOrderType.REPAIR) {
            var project = ConstructionManager.activeProject(settlement).orElse(null);
            if (project != null) {
                var blueprint = BlueprintLoader.INSTANCE.get(project.blueprintId()).orElse(null);
                if (blueprint != null) return project.origin().offset(blueprint.entrance());
            }
        }
        if (order.targetPosition() != null) return order.targetPosition();
        SettlementStorage storage = new SettlementStorage((ServerLevel) resident.level(), settlement);
        if (order.type() == WorkOrderType.PRODUCE_RESOURCE) {
            BlacksmithRecipe.Ingredient missing = missingProductionIngredient(order);
            if (missing != null) return storage.nearest(resident.blockPosition(), stack -> stack.is(missing.item())).orElse(null);
            return resident.getProfessionData().workstation();
        }
        if (order.type() == WorkOrderType.FETCH_FOOD)
            return storage.nearest(resident.blockPosition(), ItemStack::isEdible).orElse(null);
        if (order.type() == WorkOrderType.FETCH_TOOL) {
            var item = order.requirement() == Items.AIR
                    ? resident.getProfessionData().profession().requiredTool() : order.requirement();
            return item == Items.AIR ? null : storage.nearest(resident.blockPosition(), stack -> stack.is(item)).orElse(null);
        }
        if (order.type() == WorkOrderType.FETCH_RESOURCE && order.requirement() != Items.AIR)
            return storage.nearest(resident.blockPosition(), stack -> stack.is(order.requirement())).orElse(null);
        return settlement.center();
    }

    @Nullable
    private BlacksmithRecipe.Ingredient missingProductionIngredient(WorkOrder order) {
        BlacksmithRecipe recipe = BlacksmithRecipe.forResult(order.result()).orElse(null);
        if (recipe == null) return null;
        for (BlacksmithRecipe.Ingredient ingredient : recipe.ingredients) {
            if (count(resident.getNpcInventory(), ingredient.item()) < ingredient.count()) return ingredient;
        }
        return null;
    }

    private static int count(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) total += container.getItem(i).getCount();
        }
        return total;
    }

    @Nullable
    private BlockPos fleeTarget(ServerLevel level) {
        Mob nearest = level.getEntitiesOfClass(Mob.class, resident.getBoundingBox().inflate(12.0D),
                        mob -> mob.isAlive() && HostileMobIntegration.isHostileToResidents(mob))
                .stream().min(java.util.Comparator.comparingDouble(resident::distanceToSqr)).orElse(null);
        if (nearest == null) return resident.blockPosition();
        Vec3 away = resident.position().subtract(nearest.position()).normalize().scale(12.0D);
        return BlockPos.containing(resident.position().add(away));
    }

    private ItemStack firstDepositable() {
        for (int i = 0; i < resident.getNpcInventory().getContainerSize(); i++) {
            ItemStack stack = resident.getNpcInventory().getItem(i);
            if (SettlementStorage.isDepositable(resident, stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void stop() { resident.getNavigation().stop(); target = null; actionTicks = 0; }
}

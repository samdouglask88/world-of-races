package com.sam.realmfolk.society.ai;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.integration.HostileMobIntegration;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.profession.blacksmith.BlacksmithRecipe;
import com.sam.realmfolk.profession.forestry.ForestryManager;
import com.sam.realmfolk.profession.farming.FarmingManager;
import com.sam.realmfolk.profession.mining.MiningManager;
import com.sam.realmfolk.profession.cooking.CookingManager;
import com.sam.realmfolk.profession.fishing.FishingManager;
import com.sam.realmfolk.profession.hunting.HuntingManager;
import com.sam.realmfolk.profession.merchant.MerchantManager;
import com.sam.realmfolk.profession.guard.GuardManager;
import com.sam.realmfolk.society.needs.NeedsManager;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.construction.BlueprintLoader;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.TaskManager;
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
        if (target == null && resident.getNpcBrain().currentAction() != NpcAction.EAT) {
            if (resident.getNpcBrain().currentAction() == NpcAction.EXECUTE_WORK_ORDER && settlement != null) {
                TaskManager.releaseCurrent(settlement, resident);
                SettlementSavedData.get(level.getServer()).changed();
            }
            resident.getNpcBrain().clearAction("Destino indisponível");
        }
    }

    @Override
    public void tick() {
        actionTicks++;
        if (!(resident.level() instanceof ServerLevel level) || target == null) return;
        if (resident.getNpcBrain().currentAction() == NpcAction.EXECUTE_WORK_ORDER) {
            Settlement currentSettlement = settlement(level);
            WorkOrder currentOrder = order(currentSettlement);
            if (currentSettlement != null && currentOrder != null && HuntingManager.isHuntingOrder(currentOrder)) {
                BlockPos movingTarget = HuntingManager.navigationTarget(level, currentSettlement, resident, currentOrder);
                if (movingTarget != null) target = movingTarget;
            }
        }
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
        if (ForestryManager.isForestryOrder(order)) {
            if (actionTicks % ForestryManager.WORK_INTERVAL_TICKS != 0) return;
            ForestryManager.WorkResult result = ForestryManager.workTree(level, settlement, resident, order);
            switch (result) {
                case IN_PROGRESS -> {
                    order.heartbeat(resident.getPersonId(), level.getGameTime());
                    SettlementSavedData.get(level.getServer()).changed();
                }
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Árvore cortada e madeira recolhida");
                }
                case NO_TOOL, INVENTORY_FULL, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_TOOL -> "Lenhador sem machado";
                        case INVENTORY_FULL -> "Inventário cheio";
                        default -> "Chunk da árvore descarregado";
                    });
                }
                case INVALID_TREE, BLOCKED -> {
                    order.block(result == ForestryManager.WorkResult.INVALID_TREE
                            ? "Árvore removida ou deixou de ser segura" : "Bloco da árvore protegido");
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (FarmingManager.isFarmingOrder(order)) {
            if (actionTicks % FarmingManager.WORK_INTERVAL_TICKS != 0) return;
            FarmingManager.WorkResult result = FarmingManager.workCrop(level, settlement, resident, order);
            switch (result) {
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Plantação colhida e replantada");
                }
                case NO_TOOL, INVENTORY_FULL, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_TOOL -> "Fazendeiro sem enxada";
                        case INVENTORY_FULL -> "Inventário cheio";
                        default -> "Chunk da plantação descarregado";
                    });
                }
                case NO_SEED, INVALID_CROP, BLOCKED -> {
                    order.block(switch (result) {
                        case NO_SEED -> "Colheita não forneceu muda ou semente para replantio";
                        case INVALID_CROP -> "Plantação removida ou ainda não está madura";
                        default -> "Plantação protegida ou bloqueada";
                    });
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (MiningManager.isMiningOrder(order)) {
            if (actionTicks % MiningManager.WORK_INTERVAL_TICKS != 0) return;
            MiningManager.WorkResult result = MiningManager.workBlock(level, settlement, resident, order);
            switch (result) {
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Bloco minerado e recurso recolhido");
                }
                case NO_TOOL, INVENTORY_FULL, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_TOOL -> "Minerador sem picareta";
                        case INVENTORY_FULL -> "Inventário cheio";
                        default -> "Chunk da mina descarregado";
                    });
                }
                case INVALID_TARGET, BLOCKED -> {
                    order.block(result == MiningManager.WorkResult.INVALID_TARGET
                            ? "Alvo removido, inacessível ou fora da área autorizada"
                            : "Bloco da mina protegido");
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (CookingManager.isCookingOrder(order)) {
            if (CookingManager.missingIngredients(resident, order) > 0) {
                if (CookingManager.collectIngredients(level, settlement, resident, order)) {
                    order.heartbeat(resident.getPersonId(), level.getGameTime());
                    resident.getNpcBrain().clearAction("Ingrediente retirado fisicamente do armazém");
                    SettlementSavedData.get(level.getServer()).changed();
                    return;
                }
                order.block("Ingrediente reservado não está mais disponível");
                finish(settlement, false, order.blockedReason());
                return;
            }
            if (actionTicks % CookingManager.WORK_INTERVAL_TICKS != 0) return;
            CookingManager.WorkResult result = CookingManager.prepareMeal(level, settlement, resident, order);
            switch (result) {
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Refeição preparada com ingredientes do armazém");
                }
                case NO_TOOL, INVENTORY_FULL, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_TOOL -> "Cozinheiro sem utensílio";
                        case INVENTORY_FULL -> "Inventário cheio";
                        default -> "Chunk da cozinha descarregado";
                    });
                }
                case MISSING_INGREDIENTS, INVALID_STATION -> {
                    order.block(result == CookingManager.WorkResult.MISSING_INGREDIENTS
                            ? "Ingrediente reservado não está mais disponível"
                            : "Defumador da cozinha removido ou inválido");
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (FishingManager.isFishingOrder(order)) {
            if (actionTicks % FishingManager.WORK_INTERVAL_TICKS != 0) return;
            FishingManager.WorkResult result = FishingManager.fish(level, settlement, resident, order);
            switch (result) {
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Pescaria concluída e captura recolhida");
                }
                case NO_TOOL, INVENTORY_FULL, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_TOOL -> "Pescador sem vara de pesca";
                        case INVENTORY_FULL -> "Inventário cheio";
                        default -> "Chunk do ponto de pesca descarregado";
                    });
                }
                case NO_CATCH, INVALID_SPOT -> {
                    order.block(result == FishingManager.WorkResult.NO_CATCH
                            ? "A pescaria não produziu captura" : "Margem ou água do ponto de pesca deixou de ser válida");
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (HuntingManager.isHuntingOrder(order)) {
            if (HuntingManager.missingAmmunition(resident, order) > 0) {
                if (HuntingManager.collectAmmunition(level, settlement, resident, order)) {
                    order.heartbeat(resident.getPersonId(), level.getGameTime());
                    resident.getNpcBrain().clearAction("Flechas retiradas fisicamente do armazém");
                    SettlementSavedData.get(level.getServer()).changed();
                    return;
                }
                order.block("Flechas reservadas não estão mais disponíveis");
                finish(settlement, false, order.blockedReason());
                return;
            }
            if (actionTicks % HuntingManager.WORK_INTERVAL_TICKS != 0) return;
            HuntingManager.WorkResult result = HuntingManager.hunt(level, settlement, resident, order);
            switch (result) {
                case IN_PROGRESS -> {
                    order.heartbeat(resident.getPersonId(), level.getGameTime());
                    SettlementSavedData.get(level.getServer()).changed();
                }
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Caça concluída e recursos recolhidos");
                }
                case NO_TOOL, NO_AMMO, INVENTORY_FULL, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_TOOL -> "Caçador sem arco";
                        case NO_AMMO -> "Caçador sem flechas";
                        case INVENTORY_FULL -> "Inventário cheio; drops mantidos no mundo";
                        default -> "Chunk do alvo descarregado";
                    });
                }
                case INVALID_TARGET -> {
                    order.block("Alvo desapareceu ou a população protegida ficou pequena");
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (MerchantManager.isStockOrder(order)) {
            MerchantManager.WorkResult result = MerchantManager.restock(level, settlement, resident, order);
            switch (result) {
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Mercadoria retirada do armazém e colocada à venda");
                }
                case INVENTORY_FULL, MISSING_STOCK, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case INVENTORY_FULL -> "Estoque do comerciante cheio";
                        case MISSING_STOCK -> "Mercadoria reservada não está mais disponível";
                        default -> "Chunk do armazém descarregado";
                    });
                }
                case INVALID_SOURCE -> {
                    order.block("Barril de origem removido ou inválido");
                    finish(settlement, false, order.blockedReason());
                }
            }
            return;
        }
        if (GuardManager.isPatrolOrder(order)) {
            GuardManager.WorkResult result = GuardManager.patrol(level, settlement, resident, order);
            switch (result) {
                case COMPLETED -> {
                    order.complete();
                    finish(settlement, true, "Ponto de patrulha verificado");
                }
                case NO_WEAPON, THREAT_PRESENT, UNLOADED -> {
                    order.release();
                    finish(settlement, false, switch (result) {
                        case NO_WEAPON -> "Guarda sem arma";
                        case THREAT_PRESENT -> "Patrulha interrompida para defender o povoado";
                        default -> "Chunk da patrulha descarregado";
                    });
                }
                case INVALID_POINT -> {
                    order.block("Ponto de patrulha removido ou inválido");
                    finish(settlement, false, order.blockedReason());
                }
            }
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
            case GUARD -> success = false;
            case BUILD, REPAIR -> {
                ConstructionManager.WorkResult result = ConstructionManager.workProject(level, settlement, resident);
                switch (result) {
                    case COMPLETED -> { order.complete(); finish(settlement, true, "Construção concluída"); }
                    case IN_PROGRESS -> {
                        order.heartbeat(resident.getPersonId(), level.getGameTime());
                        resident.getNpcBrain().clearAction("Etapa da construção concluída");
                        SettlementSavedData.get(level.getServer()).changed();
                    }
                    case UNLOADED -> {
                        order.release();
                        finish(settlement, false, "Chunk do projeto descarregado; progresso preservado");
                    }
                    case WAITING_RESOURCES -> {
                        order.cancel();
                        finish(settlement, false, "Construção aguardando materiais");
                    }
                    case NO_TOOL -> {
                        order.release();
                        finish(settlement, false, "Construtor sem martelo");
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
        SettlementStorage storage = new SettlementStorage((ServerLevel) resident.level(), settlement);
        if (CookingManager.isCookingOrder(order) && CookingManager.missingIngredients(resident, order) > 0) {
            var ingredient = CookingManager.missingIngredient(order);
            return ingredient == null ? null
                    : storage.nearest(resident.blockPosition(), stack -> stack.is(ingredient)).orElse(null);
        }
        if (HuntingManager.isHuntingOrder(order) && HuntingManager.missingAmmunition(resident, order) > 0) {
            return storage.nearest(resident.blockPosition(), stack -> stack.is(Items.ARROW)).orElse(null);
        }
        if (order.targetPosition() != null) return order.targetPosition();
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
    public void stop() {
        resident.getNavigation().stop();
        if (actionTicks >= 400 && resident.level() instanceof ServerLevel level
                && resident.getNpcBrain().currentAction() == NpcAction.EXECUTE_WORK_ORDER) {
            Settlement settlement = settlement(level);
            if (settlement != null) {
                TaskManager.releaseCurrent(settlement, resident);
                SettlementSavedData.get(level.getServer()).changed();
            }
            resident.getNpcBrain().clearAction("Destino da tarefa inacessível");
        }
        target = null;
        actionTicks = 0;
    }
}

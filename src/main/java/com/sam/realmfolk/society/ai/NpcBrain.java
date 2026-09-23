package com.sam.realmfolk.society.ai;

import com.sam.realmfolk.entity.NpcBehaviorMode;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.integration.HostileMobIntegration;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.needs.NeedsManager;
import com.sam.realmfolk.society.needs.NeedType;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.TaskManager;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

public final class NpcBrain {
    private NpcAction currentAction = NpcAction.IDLE;
    private String reason = "Sem necessidade urgente";

    public NpcAction currentAction() { return currentAction; }
    public String reason() { return reason; }

    public void evaluate(ServerLevel level, ResidentEntity resident) {
        Settlement settlement = resident.getSettlementId() == null ? null
                : SettlementSavedData.get(level.getServer()).get(resident.getSettlementId()).orElse(null);
        boolean danger = !level.getEntitiesOfClass(Mob.class, resident.getBoundingBox().inflate(8.0D),
                mob -> mob.isAlive() && HostileMobIntegration.isHostileToResidents(mob)).isEmpty();
        boolean missingTool = resident.getProfessionData().profession() != NpcProfession.NONE
                && !ProfessionService.hasRequiredTool(resident);
        resident.getNeeds().set(NeedType.SAFETY, danger ? 100 : 0);
        resident.getNeeds().set(NeedType.TOOL, missingTool ? 80 : 0);
        resident.getNeeds().set(NeedType.WORK, settlement == null || resident.getCurrentWorkOrderId() != null ? 0 : 40);
        if (danger) {
            boolean guard = resident.getProfessionData().profession() == NpcProfession.GUARD;
            decide(guard ? NpcAction.DEFEND : NpcAction.FLEE, guard ? "Protegendo o povoado" : "Perigo imediato");
            return;
        }

        int hungerPriority = NeedEvaluator.hungerPriority(resident);
        if (hungerPriority == ActionPriority.CRITICAL_HUNGER) {
            if (NeedsManager.hasFood(resident.getNpcInventory())) decide(NpcAction.EAT, "Fome crítica");
            else if (hasStoredFood(level, settlement)) decide(NpcAction.FETCH_FOOD, "Buscando comida para fome crítica");
            else decide(NpcAction.IDLE, "Fome crítica e povoado sem comida");
            return;
        }

        if (resident.getBehaviorMode() == NpcBehaviorMode.FOLLOW) {
            decide(NpcAction.FOLLOW_PLAYER, "Ordem direta para seguir");
            return;
        }
        if (resident.getBehaviorMode() == NpcBehaviorMode.STAY) {
            decide(NpcAction.STAY, "Ordem direta para permanecer");
            return;
        }

        if (SleepAtHomeGoal.isSleepTime(level)) {
            if (settlement != null && resident.getCurrentWorkOrderId() != null) {
                TaskManager.releaseCurrent(settlement, resident);
                SettlementSavedData.get(level.getServer()).changed();
            }
            decide(NpcAction.REST, "Retornando para uma cama durante a noite");
            return;
        }

        if (hungerPriority == ActionPriority.BASIC_NEED) {
            if (NeedsManager.hasFood(resident.getNpcInventory())) decide(NpcAction.EAT, "Fome");
            else if (hasStoredFood(level, settlement)) decide(NpcAction.FETCH_FOOD, "Buscando comida no armazém");
            else decide(NpcAction.IDLE, "Povoado sem comida disponível");
            return;
        }

        if (resident.getBirthHomePosition() != null) {
            decide(NpcAction.REST, "Retornando para casa para o nascimento");
            return;
        }

        if (!resident.isWorkingAge()) {
            decide(NpcAction.IDLE, resident.isBaby() ? "Bebe sob cuidado da familia" : "Ainda nao possui idade para trabalhar");
            return;
        }

        if (settlement == null) { decide(NpcAction.IDLE, "Sem povoado"); return; }

        if (resident.getCurrentWorkOrderId() != null) {
            WorkOrder order = settlement.taskBoard().get(resident.getCurrentWorkOrderId()).orElse(null);
            if (order != null && order.assignedNpcId() != null && order.assignedNpcId().equals(resident.getPersonId())
                    && (order.status() == WorkOrderStatus.RESERVED || order.status() == WorkOrderStatus.IN_PROGRESS)) {
                order.heartbeat(resident.getPersonId(), level.getGameTime());
                decide(NpcAction.EXECUTE_WORK_ORDER, "Executando " + order.type().name());
                return;
            }
            settlement.releaseStorageReservations(resident.getCurrentWorkOrderId());
            resident.setCurrentWorkOrderId(null);
        }

        if (missingTool) {
            NpcProfession profession = resident.getProfessionData().profession();
            if (new SettlementStorage(level, settlement).count(profession.requiredTool()) > 0) {
                decide(NpcAction.FETCH_TOOL, "Buscando " + profession.tool());
                return;
            }
        }

        if (TaskManager.reserveBest(level, settlement, resident).isPresent()) {
            decide(NpcAction.EXECUTE_WORK_ORDER, "Nova ordem de trabalho reservada");
            return;
        }

        if (SettlementStorage.hasDepositable(resident) && new SettlementStorage(level, settlement)
                .nearestWithSpace(resident.blockPosition(), firstDepositable(resident)).isPresent()) {
            decide(NpcAction.DEPOSIT_ITEMS, "Inventário com excedentes");
            return;
        }
        decide(NpcAction.IDLE, "Aguardando trabalho");
    }

    public void clearAction(String newReason) { decide(NpcAction.IDLE, newReason); }

    private void decide(NpcAction action, String why) { currentAction = action; reason = why; }

    private static boolean hasStoredFood(ServerLevel level, @Nullable Settlement settlement) {
        return settlement != null && new SettlementStorage(level, settlement).countFood() > 0;
    }

    private static net.minecraft.world.item.ItemStack firstDepositable(ResidentEntity resident) {
        for (int i = 0; i < resident.getNpcInventory().getContainerSize(); i++) {
            var stack = resident.getNpcInventory().getItem(i);
            if (SettlementStorage.isDepositable(resident, stack)) return stack;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}

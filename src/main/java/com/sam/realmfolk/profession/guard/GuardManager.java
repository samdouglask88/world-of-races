package com.sam.realmfolk.profession.guard;

import com.sam.realmfolk.content.ModBlocks;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Turns registered patrol markers into real guard work orders. */
public final class GuardManager {
    public static final int MAX_OPEN_ORDERS = 2;

    private GuardManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS
                || readyGuard(level, settlement) == null) return false;
        List<BlockPos> points = new ArrayList<>(settlement.patrolPositions());
        if (points.isEmpty()) return false;
        int start = (int) ((level.getGameTime() / 600L) % points.size());
        for (int offset = 0; offset < points.size(); offset++) {
            BlockPos point = points.get((start + offset) % points.size());
            if (validPoint(level, settlement, point) && !hasOrderFor(settlement, point)) {
                return createOrderAt(level, settlement, point) != null;
            }
        }
        return false;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity guard, WorkOrder order) {
        return isPatrolOrder(order) && guard.getProfessionData().profession() == NpcProfession.GUARD
                && guard.getTarget() == null && ProfessionService.validateOrFindStation(level, guard)
                && ProfessionService.hasRequiredTool(guard)
                && order.targetPosition() != null && validPoint(level, settlement, order.targetPosition());
    }

    public static WorkResult patrol(ServerLevel level, Settlement settlement,
                                    ResidentEntity guard, WorkOrder order) {
        BlockPos point = order.targetPosition();
        if (point == null || !settlement.contains(point)) return WorkResult.INVALID_POINT;
        if (!level.hasChunkAt(point)) return WorkResult.UNLOADED;
        if (!validPoint(level, settlement, point)) return WorkResult.INVALID_POINT;
        if (!ProfessionService.hasRequiredTool(guard)) return WorkResult.NO_WEAPON;
        if (guard.getTarget() != null && guard.getTarget().isAlive()) return WorkResult.THREAT_PRESENT;
        guard.getProfessionData().addExperience(6);
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static WorkOrder createOrderAt(ServerLevel level, Settlement settlement, BlockPos point) {
        if (!validPoint(level, settlement, point) || hasOrderFor(settlement, point)) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.GUARD,
                Math.max(60, settlement.government().priority(SettlementPolicy.DEFENSE)), settlement.id(),
                point, level.dimension(), Items.IRON_SWORD, 1, Items.AIR, 0,
                now, now + DailyEconomyManager.DAY_TICKS);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean isPatrolOrder(WorkOrder order) {
        return order.type() == WorkOrderType.GUARD && order.targetPosition() != null;
    }

    private static boolean validPoint(ServerLevel level, Settlement settlement, BlockPos point) {
        return settlement.contains(point) && settlement.patrolPositions().contains(point)
                && level.hasChunkAt(point) && level.getBlockState(point).is(ModBlocks.PATROL_MARKER.get());
    }

    @Nullable
    private static ResidentEntity readyGuard(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity guard && guard.isAlive() && guard.isWorkingAge()
                    && guard.getProfessionData().profession() == NpcProfession.GUARD
                    && guard.getTarget() == null && ProfessionService.validateOrFindStation(level, guard)
                    && ProfessionService.hasRequiredTool(guard)) return guard;
        }
        return null;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isPatrolOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOrderFor(Settlement settlement, BlockPos point) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isPatrolOrder(order) && point.equals(order.targetPosition())
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    public enum WorkResult { COMPLETED, NO_WEAPON, THREAT_PRESENT, UNLOADED, INVALID_POINT }
}

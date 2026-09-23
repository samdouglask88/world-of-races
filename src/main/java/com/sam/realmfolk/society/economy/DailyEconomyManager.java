package com.sam.realmfolk.society.economy;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.PersonStatus;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementEvolutionManager;
import com.sam.realmfolk.society.settlement.SettlementManager;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.reproduction.ReproductionManager;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DailyEconomyManager {
    public static final long DAY_TICKS = 24000L;
    public static final int TAX_PERCENT = 10;
    public static final int SALARY = 1;

    private DailyEconomyManager() {}

    public static void run(ServerLevel level, Settlement settlement) {
        long now = level.getGameTime();
        if (now - settlement.lastDailyUpdate() < DAY_TICKS) return;
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        int removed = validateMembers(level, settlement, society);
        SettlementStorage storage = new SettlementStorage(level, settlement);
        int population = settlement.memberIds().size();
        int food = storage.countFood();
        Treasury treasury = new Treasury(level, settlement);
        int balanceBefore = treasury.balance();
        int unemployed = 0;
        int missingTools = 0;
        int threats = level.getEntitiesOfClass(Monster.class,
                AABB.ofSize(settlement.center().getCenter(), settlement.radius() * 2.0D, 64.0D,
                        settlement.radius() * 2.0D), Entity::isAlive).size();
        Set<NpcProfession> activeProfessions = new HashSet<>();

        for (UUID personId : settlement.memberIds()) {
            ResidentEntity resident = loaded(level, society.getPerson(personId).orElse(null));
            if (resident == null || !resident.isWorkingAge()) continue;
            NpcProfession profession = resident.getProfessionData().profession();
            if (profession == NpcProfession.NONE) unemployed++;
            else activeProfessions.add(profession);
            if (profession != NpcProfession.NONE && !ProfessionService.hasRequiredTool(resident)) missingTools++;
        }

        updatePriorities(settlement, population, food, balanceBefore, missingTools, threats);
        createMissingOrders(level, settlement, food, missingTools,
                activeProfessions.contains(NpcProfession.BLACKSMITH));
        ConstructionManager.evaluateStorageNeed(level, settlement);
        ConstructionManager.ensureBuildOrder(level, settlement);
        ReproductionManager.dailyUpdate(level, settlement);
        int taxCollected = collectTaxes(level, settlement, society, treasury, now);
        int salariesPaid = paySalaries(level, settlement, society, treasury, now);
        settlement.taskBoard().maintain(now);
        settlement.setLastDailyUpdate(now);
        settlement.economy().setLastDailySummary("População " + population + ", comida " + food
                + ", tesouro " + treasury.balance() + ", desempregados " + unemployed
                + ", ameaças " + threats + ", impostos " + taxCollected
                + ", salários " + salariesPaid + ", removidos " + removed + ".");
        if (settlement.leaderId() == null) SettlementManager.selectLeader(level, settlement);
        SettlementEvolutionManager.evaluate(level, settlement);
        SettlementSavedData.get(level.getServer()).changed();
    }

    private static int validateMembers(ServerLevel level, Settlement settlement, HumanSocietySavedData society) {
        int removed = 0;
        Iterator<UUID> iterator = new HashSet<>(settlement.memberIds()).iterator();
        while (iterator.hasNext()) {
            UUID personId = iterator.next();
            PersonData person = society.getPerson(personId).orElse(null);
            if (person == null || person.getStatus() != PersonStatus.ALIVE) {
                if (settlement.removeMember(personId)) removed++;
            }
        }
        return removed;
    }

    private static void updatePriorities(Settlement settlement, int population, int food, int treasury,
                                         int missingTools, int threats) {
        int foodTarget = Math.max(16, population * 6);
        settlement.government().setPriority(SettlementPolicy.FOOD, food < foodTarget ? 95 : 45);
        settlement.government().setPriority(SettlementPolicy.PRODUCTION, missingTools > 0 ? 85 : 55);
        settlement.government().setPriority(SettlementPolicy.DEFENSE, threats >= 3 ? 95 : threats > 0 ? 75 : 45);
        settlement.government().setPriority(SettlementPolicy.HOUSING, settlement.projects().isEmpty() ? 70 : 45);
        settlement.government().setPriority(SettlementPolicy.WEALTH, treasury < 16 ? 80 : 45);
    }

    private static void createMissingOrders(ServerLevel level, Settlement settlement, int food, int missingTools,
                                            boolean hasBlacksmith) {
        long now = level.getGameTime();
        if (food < Math.max(16, settlement.memberIds().size() * 6) && !hasOpen(settlement, WorkOrderType.FETCH_FOOD)) {
            settlement.taskBoard().add(new WorkOrder(UUID.randomUUID(), WorkOrderType.FETCH_FOOD,
                    settlement.government().priority(SettlementPolicy.FOOD), settlement.id(), null, level.dimension(),
                    Items.BREAD, 1, Items.AIR, 0, now, now + DAY_TICKS));
        }
        if (missingTools > 0 && !hasOpen(settlement, WorkOrderType.FETCH_TOOL)) {
            settlement.taskBoard().add(new WorkOrder(UUID.randomUUID(), WorkOrderType.FETCH_TOOL,
                    settlement.government().priority(SettlementPolicy.PRODUCTION), settlement.id(), null, level.dimension(),
                    Items.AIR, 1, Items.AIR, 0, now, now + DAY_TICKS));
        }
        if (hasBlacksmith && !hasOpen(settlement, WorkOrderType.PRODUCE_RESOURCE)) {
            settlement.taskBoard().add(new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                    settlement.government().priority(SettlementPolicy.PRODUCTION), settlement.id(), null, level.dimension(),
                    Items.IRON_INGOT, 2, Items.IRON_SWORD, 1, now, now + DAY_TICKS * 2));
        }
    }

    private static boolean hasOpen(Settlement settlement, WorkOrderType type) {
        return settlement.taskBoard().orders().stream().anyMatch(order -> order.type() == type
                && order.status() != WorkOrderStatus.COMPLETED && order.status() != WorkOrderStatus.CANCELLED);
    }

    private static int collectTaxes(ServerLevel level, Settlement settlement, HumanSocietySavedData society,
                                    Treasury treasury, long now) {
        int total = 0;
        for (Map.Entry<UUID, Integer> entry : settlement.economy().consumeIncomeLedger().entrySet()) {
            int due = taxDue(entry.getValue());
            if (due <= 0) continue;
            ResidentEntity resident = loaded(level, society.getPerson(entry.getKey()).orElse(null));
            if (resident == null) {
                settlement.economy().recordIncome(entry.getKey(), entry.getValue());
                continue;
            }
            int remaining = due;
            int fromTrade = Math.min(remaining, countEmeralds(resident.getTradeInventory()));
            if (fromTrade > 0 && treasury.depositFrom(resident.getTradeInventory(), fromTrade,
                    EconomyTransactionType.TAX, now, entry.getKey(), "Imposto sobre renda registrada")) {
                total += fromTrade;
                remaining -= fromTrade;
            }
            int fromWallet = Math.min(remaining, countEmeralds(resident.getNpcInventory()));
            if (fromWallet > 0 && treasury.depositFrom(resident.getNpcInventory(), fromWallet,
                    EconomyTransactionType.TAX, now, entry.getKey(), "Imposto sobre renda registrada")) {
                total += fromWallet;
            }
        }
        return total;
    }

    public static int taxDue(int registeredIncome) {
        return Math.max(0, registeredIncome) * TAX_PERCENT / 100;
    }

    private static int paySalaries(ServerLevel level, Settlement settlement, HumanSocietySavedData society,
                                   Treasury treasury, long now) {
        int paid = 0;
        for (UUID personId : settlement.memberIds()) {
            ResidentEntity resident = loaded(level, society.getPerson(personId).orElse(null));
            if (resident == null || !resident.isWorkingAge()
                    || resident.getProfessionData().profession() == NpcProfession.NONE) continue;
            if (treasury.withdrawTo(resident.getNpcInventory(), SALARY, EconomyTransactionType.SALARY,
                    now, personId, "Salário diário")) paid += SALARY;
        }
        return paid;
    }

    private static int countEmeralds(net.minecraft.world.Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.EMERALD)) total += container.getItem(i).getCount();
        return total;
    }

    private static ResidentEntity loaded(ServerLevel level, PersonData person) {
        if (person == null || person.getEntityId() == null) return null;
        Entity entity = level.getEntity(person.getEntityId());
        return entity instanceof ResidentEntity resident && resident.isAlive() ? resident : null;
    }
}

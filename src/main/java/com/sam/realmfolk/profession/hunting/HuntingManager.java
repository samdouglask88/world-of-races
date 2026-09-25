package com.sam.realmfolk.profession.hunting;

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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Physical, population-safe hunting with real arrows, damage and mob drops. */
public final class HuntingManager {
    public static final int WORK_INTERVAL_TICKS = 30;
    public static final int ARROWS_PER_ORDER = 4;
    public static final int MIN_POPULATION = 5;
    public static final int MAX_OPEN_ORDERS = 1;
    private static final double TARGET_SEARCH_RADIUS = 12.0D;
    private static final double SHOOT_RANGE_SQR = 12.0D * 12.0D;

    private HuntingManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        ResidentEntity hunter = readyHunter(level, settlement);
        if (hunter == null) return false;
        Animal prey = findPrey(level, settlement, hunter.blockPosition());
        return prey != null && createOrderFor(level, settlement, prey) != null;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity resident, WorkOrder order) {
        if (!isHuntingOrder(order) || resident.getProfessionData().profession() != NpcProfession.HUNTER
                || !ProfessionService.validateOrFindStation(level, resident)
                || !ProfessionService.hasRequiredTool(resident)) return false;
        int arrows = count(resident.getNpcInventory(), Items.ARROW)
                + availableArrows(level, settlement, new SettlementStorage(level, settlement));
        return arrows >= ARROWS_PER_ORDER && acquirePrey(level, settlement, resident, order) != null;
    }

    public static WorkResult hunt(ServerLevel level, Settlement settlement,
                                  ResidentEntity resident, WorkOrder order) {
        if (!isHuntingOrder(order) || order.targetPosition() == null
                || !settlement.contains(order.targetPosition())) return WorkResult.INVALID_TARGET;
        if (!level.hasChunkAt(order.targetPosition())) return WorkResult.UNLOADED;
        if (!ProfessionService.hasRequiredTool(resident)) return WorkResult.NO_TOOL;

        Animal prey = resident.getTarget() instanceof Animal animal && supported(animal) ? animal : null;
        if (prey != null && !prey.isAlive()) {
            BlockPos deathPosition = prey.blockPosition();
            if (collectDrops(level, resident, deathPosition) < 0) return WorkResult.INVENTORY_FULL;
            resident.setTarget(null);
            resident.getProfessionData().addExperience(18);
            return WorkResult.COMPLETED;
        }
        if (prey == null || !validPrey(level, settlement, prey, population(level, settlement))) {
            prey = acquirePrey(level, settlement, resident, order);
            if (prey == null) {
                int collected = collectDrops(level, resident, resident.blockPosition());
                if (collected < 0) return WorkResult.INVENTORY_FULL;
                if (collected > 0) {
                    resident.getProfessionData().addExperience(18);
                    return WorkResult.COMPLETED;
                }
                return WorkResult.INVALID_TARGET;
            }
        }
        if (!level.hasChunkAt(prey.blockPosition())) return WorkResult.UNLOADED;
        resident.setTarget(prey);
        if (resident.distanceToSqr(prey) > SHOOT_RANGE_SQR || !resident.getSensing().hasLineOfSight(prey)) {
            resident.getNavigation().moveTo(prey, 1.1D);
            return WorkResult.IN_PROGRESS;
        }
        if (!consumeArrow(resident)) return WorkResult.NO_AMMO;
        resident.getNavigation().stop();
        resident.getLookControl().setLookAt(prey, 30.0F, 30.0F);
        shoot(level, resident, prey);
        ProfessionService.damageRequiredTool(resident);
        return WorkResult.IN_PROGRESS;
    }

    public static boolean reserveAmmunition(ServerLevel level, Settlement settlement,
                                            ResidentEntity resident, WorkOrder order) {
        if (!isHuntingOrder(order)) return false;
        int owned = count(resident.getNpcInventory(), Items.ARROW);
        int needed = Math.max(0, ARROWS_PER_ORDER - owned);
        if (needed == 0) return true;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        if (availableArrows(level, settlement, storage) < needed) return false;
        return settlement.reserveStorage(order.id(), Items.ARROW, needed,
                level.getGameTime() + WorkOrder.DEFAULT_RESERVATION_TICKS);
    }

    @Nullable
    public static WorkOrder createOrderFor(ServerLevel level, Settlement settlement, Animal prey) {
        Map<EntityType<?>, Integer> population = population(level, settlement);
        if (!validPrey(level, settlement, prey, population) || hasNearbyOrder(settlement, prey.blockPosition())) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(65, settlement.government().priority(SettlementPolicy.FOOD)), settlement.id(),
                prey.blockPosition(), level.dimension(), Items.BOW, 1, expectedDrop(prey), 1,
                now, now + DailyEconomyManager.DAY_TICKS);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean isHuntingOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE && order.targetPosition() != null
                && order.requirement() == Items.BOW && order.requiredAmount() == 1;
    }

    @Nullable
    public static BlockPos navigationTarget(ServerLevel level, Settlement settlement,
                                            ResidentEntity resident, WorkOrder order) {
        if (missingAmmunition(resident, order) > 0) {
            return new SettlementStorage(level, settlement)
                    .nearest(resident.blockPosition(), stack -> stack.is(Items.ARROW)).orElse(null);
        }
        Animal prey = resident.getTarget() instanceof Animal animal && supported(animal) ? animal : null;
        if (prey == null) prey = acquirePrey(level, settlement, resident, order);
        return prey == null ? order.targetPosition() : prey.blockPosition();
    }

    @Nullable
    private static Animal acquirePrey(ServerLevel level, Settlement settlement,
                                      ResidentEntity resident, WorkOrder order) {
        Animal current = resident.getTarget() instanceof Animal animal ? animal : null;
        Map<EntityType<?>, Integer> population = population(level, settlement);
        if (current != null && validPrey(level, settlement, current, population)) return current;
        BlockPos origin = order.targetPosition();
        if (origin == null) return null;
        Animal best = null;
        double bestDistance = TARGET_SEARCH_RADIUS * TARGET_SEARCH_RADIUS;
        AABB area = new AABB(origin).inflate(TARGET_SEARCH_RADIUS);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, area)) {
            if (!validPrey(level, settlement, animal, population)) continue;
            double distance = animal.blockPosition().distSqr(origin);
            if (best == null || distance < bestDistance) {
                best = animal;
                bestDistance = distance;
            }
        }
        if (best != null) resident.setTarget(best);
        return best;
    }

    @Nullable
    private static Animal findPrey(ServerLevel level, Settlement settlement, BlockPos hunterPosition) {
        Map<EntityType<?>, Integer> population = population(level, settlement);
        Animal best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Animal animal : animalsInSettlement(level, settlement)) {
            if (!validPrey(level, settlement, animal, population) || hasNearbyOrder(settlement, animal.blockPosition())) continue;
            double distance = animal.blockPosition().distSqr(hunterPosition);
            if (distance < bestDistance) {
                best = animal;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Map<EntityType<?>, Integer> population(ServerLevel level, Settlement settlement) {
        Map<EntityType<?>, Integer> population = new HashMap<>();
        for (Animal animal : animalsInSettlement(level, settlement)) {
            if (supported(animal) && animal.isAlive() && !animal.isBaby()) {
                population.merge(animal.getType(), 1, Integer::sum);
            }
        }
        return population;
    }

    private static List<Animal> animalsInSettlement(ServerLevel level, Settlement settlement) {
        double radius = settlement.radius();
        AABB area = new AABB(settlement.center()).inflate(radius, 32.0D, radius);
        return level.getEntitiesOfClass(Animal.class, area,
                animal -> settlement.contains(animal.blockPosition()) && level.hasChunkAt(animal.blockPosition()));
    }

    private static boolean validPrey(ServerLevel level, Settlement settlement, Animal animal,
                                     Map<EntityType<?>, Integer> population) {
        return animal.isAlive() && supported(animal) && !animal.isBaby() && !animal.hasCustomName()
                && !animal.isPersistenceRequired() && !animal.isLeashed()
                && !animal.isPassenger() && !animal.isVehicle()
                && (!(animal instanceof TamableAnimal tamable) || !tamable.isTame())
                && !animal.isInvulnerable() && settlement.contains(animal.blockPosition())
                && level.hasChunkAt(animal.blockPosition())
                && population.getOrDefault(animal.getType(), 0) >= MIN_POPULATION;
    }

    private static boolean supported(Animal animal) {
        return animal instanceof Cow || animal instanceof Pig || animal instanceof Sheep
                || animal instanceof Chicken || animal instanceof Rabbit;
    }

    private static Item expectedDrop(Animal animal) {
        if (animal instanceof Cow) return Items.BEEF;
        if (animal instanceof Pig) return Items.PORKCHOP;
        if (animal instanceof Sheep) return Items.MUTTON;
        if (animal instanceof Chicken) return Items.CHICKEN;
        return Items.RABBIT;
    }

    private static void shoot(ServerLevel level, ResidentEntity resident, Animal prey) {
        Arrow arrow = new Arrow(level, resident);
        arrow.setBaseDamage(4.0D);
        arrow.setPos(resident.getX(), resident.getEyeY() - 0.1D, resident.getZ());
        double dx = prey.getX() - resident.getX();
        double dz = prey.getZ() - resident.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = prey.getY(0.5D) - arrow.getY() + horizontal * 0.08D;
        arrow.shoot(dx, dy, dz, 1.6F, 2.0F);
        level.addFreshEntity(arrow);
    }

    public static int missingAmmunition(ResidentEntity resident, WorkOrder order) {
        if (!isHuntingOrder(order)) return 0;
        return Math.max(0, ARROWS_PER_ORDER - count(resident.getNpcInventory(), Items.ARROW));
    }

    public static boolean collectAmmunition(ServerLevel level, Settlement settlement,
                                            ResidentEntity resident, WorkOrder order) {
        int needed = missingAmmunition(resident, order);
        return needed == 0 || new SettlementStorage(level, settlement)
                .moveItemTo(Items.ARROW, needed, resident.getNpcInventory());
    }

    private static boolean consumeArrow(ResidentEntity resident) {
        return removeOne(resident.getNpcInventory(), Items.ARROW);
    }

    private static int collectDrops(ServerLevel level, ResidentEntity resident, BlockPos position) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(position).inflate(4.0D), item -> item.isAlive() && huntingDrop(item.getItem()));
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity drop : drops) stacks.add(drop.getItem().copy());
        if (!canFitAll(resident.getNpcInventory(), stacks)) return -1;
        for (ItemEntity drop : drops) {
            SettlementStorage.add(resident.getNpcInventory(), drop.getItem().copy());
            drop.discard();
        }
        if (!drops.isEmpty()) resident.getNpcInventory().setChanged();
        return drops.size();
    }

    private static boolean huntingDrop(ItemStack stack) {
        return stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON)
                || stack.is(Items.CHICKEN) || stack.is(Items.RABBIT) || stack.is(Items.LEATHER)
                || stack.is(Items.FEATHER) || stack.is(Items.RABBIT_HIDE) || stack.is(Items.RABBIT_FOOT)
                || stack.is(ItemTags.WOOL);
    }

    @Nullable
    private static ResidentEntity readyHunter(ServerLevel level, Settlement settlement) {
        SettlementStorage storage = new SettlementStorage(level, settlement);
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.HUNTER
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)
                    && count(resident.getNpcInventory(), Items.ARROW)
                    + availableArrows(level, settlement, storage) >= ARROWS_PER_ORDER) return resident;
        }
        return null;
    }

    private static int availableArrows(ServerLevel level, Settlement settlement, SettlementStorage storage) {
        return Math.max(0, storage.count(Items.ARROW)
                - settlement.reservedAmount(Items.ARROW, level.getGameTime()));
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isHuntingOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasNearbyOrder(Settlement settlement, BlockPos position) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isHuntingOrder(order) && order.targetPosition() != null
                    && order.targetPosition().distSqr(position) <= 16.0D
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    private static boolean removeOne(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
            container.setChanged();
            return true;
        }
        return false;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount();
        }
        return count;
    }

    private static boolean canFitAll(SimpleContainer inventory, List<ItemStack> incoming) {
        SimpleContainer simulation = new SimpleContainer(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            simulation.setItem(slot, inventory.getItem(slot).copy());
        }
        for (ItemStack stack : incoming) if (!simulation.addItem(stack.copy()).isEmpty()) return false;
        return true;
    }

    public enum WorkResult {
        IN_PROGRESS, COMPLETED, NO_TOOL, NO_AMMO, INVENTORY_FULL, UNLOADED, INVALID_TARGET
    }
}

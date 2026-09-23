package com.sam.realmfolk.gametest;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.content.ModBlocks;
import com.sam.realmfolk.content.ModItems;
import com.sam.realmfolk.entity.ModEntityTypes;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.integration.HostileMobIntegration;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.society.economy.EconomyTransactionType;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.economy.Treasury;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.construction.ConstructionProject;
import com.sam.realmfolk.society.construction.ConstructionStage;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.needs.NeedType;
import com.sam.realmfolk.society.needs.NeedsManager;
import com.sam.realmfolk.society.ai.SleepAtHomeGoal;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementEvolutionManager;
import com.sam.realmfolk.society.settlement.SettlementManager;
import com.sam.realmfolk.society.settlement.SettlementLevel;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import com.sam.realmfolk.society.FamilyManager;
import com.sam.realmfolk.society.Gender;
import com.sam.realmfolk.society.HouseholdData;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.housing.HousingManager;
import com.sam.realmfolk.society.housing.SettlementResidence;
import com.sam.realmfolk.society.reproduction.ReproductionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Realmfolk.MODID)
@PrefixGameTestTemplate(false)
public final class SettlementGameTests {
    private SettlementGameTests() {}

    @GameTest(template = "empty")
    public static void hostileMobAcquiresResidentAsCombatTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResidentEntity resident = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(3, 1, 3));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 1, 3));
        helper.assertTrue(HostileMobIntegration.tryAcquireResidentTarget(level, zombie),
                "O zumbi nao reconheceu o habitante como alvo");
        helper.assertTrue(zombie.getTarget() == resident,
                "O alvo fisico do zumbi nao foi definido como o habitante");
        zombie.discard();
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardRespondsWhenSettlementResidentIsThreatened(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID settlementId = UUID.randomUUID();
        ResidentEntity civilian = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(3, 1, 3));
        ResidentEntity guard = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(5, 1, 3));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 1, 3));
        civilian.setSettlementId(settlementId);
        guard.setSettlementId(settlementId);
        guard.getProfessionData().assign(NpcProfession.GUARD, guard.blockPosition(), level.dimension().location());
        int alerted = HostileMobIntegration.alertGuards(level, civilian, zombie);
        helper.assertTrue(alerted == 1 && guard.getTarget() == zombie,
                "O guarda nao respondeu ao ataque contra um morador do mesmo povoado");
        zombie.discard();
        civilian.discard();
        guard.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void genderedEggsCreateTheRequestedResidentGender(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResidentEntity man = ModEntityTypes.RESIDENT.get().create(level);
        ResidentEntity woman = ModEntityTypes.RESIDENT.get().create(level);
        helper.assertTrue(man != null && woman != null, "Os habitantes dos ovos nao foram criados");
        man.readAdditionalSaveData(ModItems.MALE_RESIDENT_SPAWN_EGG.get().getDefaultInstance()
                .getTagElement("EntityTag"));
        woman.readAdditionalSaveData(ModItems.FEMALE_RESIDENT_SPAWN_EGG.get().getDefaultInstance()
                .getTagElement("EntityTag"));
        man.moveTo(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        woman.moveTo(helper.absolutePos(new BlockPos(2, 1, 1)).getCenter());
        helper.assertTrue(level.addFreshEntity(man) && level.addFreshEntity(woman),
                "Os habitantes dos ovos nao entraram no mundo");
        PersonData manData = man.getPerson(level).orElse(null);
        PersonData womanData = woman.getPerson(level).orElse(null);
        helper.assertTrue(man.isMale() && !woman.isMale(), "O genero fisico dos ovos foi ignorado");
        helper.assertTrue(manData != null && manData.getGender() == Gender.MALE
                        && womanData != null && womanData.getGender() == Gender.FEMALE,
                "O genero nao foi persistido nos dados familiares");
        man.discard();
        woman.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void residentSleepsInARealBedAndReleasesItAtMorning(GameTestHelper helper) {
        buildClosedHome(helper);
        ServerLevel level = helper.getLevel();
        level.setDayTime(14000L);
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        PersonData person = new FamilyManager(society)
                .createPerson("Dorminhoco", null, Gender.MALE, LifeStage.ADULT);
        BlockPos bed = helper.absolutePos(new BlockPos(4, 1, 4));
        Settlement settlement = new Settlement(UUID.randomUUID(), "Sono", bed,
                level.dimension(), 16, level.getGameTime());
        settlement.addMember(person.getPersonId(), level.getGameTime());
        helper.assertTrue(HousingManager.refresh(level, settlement), "A cama da casa nao foi registrada");
        helper.assertTrue(com.sam.realmfolk.society.settlement.SettlementSavedData
                .get(level.getServer()).add(settlement), "O povoado de teste nao foi registrado");

        ResidentEntity resident = ModEntityTypes.RESIDENT.get().create(level);
        helper.assertTrue(resident != null, "O habitante do teste de sono nao foi criado");
        resident.initializePerson(person);
        resident.setSettlementId(settlement.id());
        BlockPos besideBed = helper.absolutePos(new BlockPos(3, 1, 4));
        resident.moveTo(besideBed.getX() + 0.5D, besideBed.getY(), besideBed.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(resident), "O habitante do teste de sono nao entrou no mundo");
        resident.getNpcBrain().evaluate(level, resident);
        SleepAtHomeGoal sleep = new SleepAtHomeGoal(resident);
        helper.assertTrue(sleep.canUse(), "O habitante nao encontrou a cama durante a noite");
        sleep.start();
        sleep.tick();
        helper.assertTrue(resident.isSleeping(), "O habitante nao assumiu a pose de sono na cama");
        helper.assertTrue(level.getBlockState(bed).getValue(BedBlock.OCCUPIED), "A cama nao foi reservada");
        for (int i = 0; i < 21; i++) sleep.tick();
        helper.assertTrue(resident.getNeeds().get(NeedType.REST) == 0, "Dormir nao recuperou o descanso");

        level.setDayTime(1000L);
        helper.assertFalse(sleep.canContinueToUse(), "O habitante tentou continuar dormindo de dia");
        sleep.stop();
        helper.assertFalse(resident.isSleeping(), "O habitante nao levantou pela manha");
        helper.assertFalse(level.getBlockState(bed).getValue(BedBlock.OCCUPIED), "A cama permaneceu ocupada");
        resident.discard();
        com.sam.realmfolk.society.settlement.SettlementSavedData.get(level.getServer()).remove(settlement.id());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allRealmfolkItemRecipesLoad(GameTestHelper helper) {
        String[] recipeIds = {
                "blacksmith_hammer", "builder_hammer", "construction_blueprint", "cook_ladle", "cradle",
                "family_certificate", "family_register", "guard_badge", "guard_horn", "healer_satchel",
                "kitchen_knife", "leaders_seal", "patrol_marker", "project_board", "provisions_pack",
                "residence_deed", "residence_marker", "settlement_charter", "settlement_ledger", "simple_gift",
                "storage_marker", "surveyors_rod", "treasury_marker", "wedding_ring", "wooden_toy",
                "work_contract", "work_marker", "work_order_book"
        };
        for (String recipeId : recipeIds) {
            ResourceLocation id = ResourceLocation.tryParse(Realmfolk.MODID + ":" + recipeId);
            helper.assertTrue(id != null && helper.getLevel().getRecipeManager().byKey(id).isPresent(),
                    "Receita ausente: " + recipeId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void workOrderRejectsDoubleReservation(GameTestHelper helper) {
        UUID settlementId = UUID.randomUUID();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.GUARD, 50, settlementId,
                null, helper.getLevel().dimension(), Items.AIR, 0, Items.AIR, 0, 0L, 24000L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        helper.assertTrue(order.reserve(first, 10L), "A primeira reserva deveria ser aceita");
        helper.assertFalse(order.reserve(second, 10L), "Uma segunda pessoa não pode reservar a mesma ordem");
        order.release();
        helper.assertTrue(order.reserve(second, 20L), "A ordem liberada deveria aceitar outra pessoa");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void settlementRoundTripPreservesCollectiveState(GameTestHelper helper) {
        UUID member = UUID.randomUUID();
        Settlement original = new Settlement(UUID.randomUUID(), "Teste", helper.absolutePos(new BlockPos(2, 1, 2)),
                helper.getLevel().dimension(), 64, 100L);
        original.addMember(member, 110L);
        original.setLeaderId(member);
        original.government().setPriority(SettlementPolicy.FOOD, 93);
        original.economy().recordIncome(member, 20);
        BlockPos storage = helper.absolutePos(new BlockPos(1, 1, 1));
        original.addStorage(storage);
        BlockPos patrol = storage.east();
        BlockPos work = storage.west();
        original.addPatrolPosition(patrol);
        original.addWorkPosition(work);
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.FETCH_RESOURCE, 70, original.id(),
                storage, helper.getLevel().dimension(), Items.COBBLESTONE, 4, Items.AIR, 0, 120L, 2000L);
        original.taskBoard().add(order);
        original.reserveStorage(order.id(), Items.COBBLESTONE, 4, 1300L);
        SettlementResidence residence = new SettlementResidence(UUID.randomUUID(), storage.above(),
                storage.above().north(), 6);
        residence.setHouseholdId(UUID.randomUUID());
        original.addResidence(residence);

        Settlement loaded = Settlement.load(original.save());
        helper.assertTrue(loaded.id().equals(original.id()), "O ID do povoado não persistiu");
        helper.assertTrue(loaded.memberIds().contains(member) && member.equals(loaded.leaderId()),
                "Membro ou líder não persistiu");
        helper.assertTrue(loaded.government().priority(SettlementPolicy.FOOD) == 93,
                "Prioridade do governo não persistiu");
        helper.assertTrue(loaded.storagePositions().contains(storage), "Armazém não persistiu");
        helper.assertTrue(loaded.patrolPositions().contains(patrol) && loaded.workPositions().contains(work),
                "Marcos de patrulha ou trabalho nao persistiram");
        helper.assertTrue(loaded.taskBoard().get(order.id()).isPresent()
                        && loaded.taskBoard().get(order.id()).get().status() == WorkOrderStatus.AVAILABLE,
                "Ordem não persistiu");
        helper.assertTrue(loaded.storageReservations().size() == 1, "Reserva de recurso não persistiu");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storageAndTreasuryMovePhysicalItems(GameTestHelper helper) {
        BlockPos relativeBarrel = new BlockPos(1, 1, 1);
        helper.setBlock(relativeBarrel, Blocks.BARREL);
        BlockPos barrelPosition = helper.absolutePos(relativeBarrel);
        Container barrel = (Container) helper.getLevel().getBlockEntity(barrelPosition);
        helper.assertTrue(barrel != null, "O barril de teste não foi criado");
        barrel.setItem(0, new ItemStack(Items.BREAD, 3));

        Settlement settlement = new Settlement(UUID.randomUUID(), "Tesouro", barrelPosition,
                helper.getLevel().dimension(), 64, helper.getLevel().getGameTime());
        settlement.addStorage(barrelPosition);
        SettlementStorage storage = new SettlementStorage(helper.getLevel(), settlement);
        SimpleContainer foodTarget = new SimpleContainer(3);
        helper.assertTrue(storage.moveOneFoodTo(foodTarget), "A comida física não saiu do barril");
        helper.assertTrue(storage.countFood() == 2 && foodTarget.countItem(Items.BREAD) == 1,
                "A transferência de comida duplicou ou perdeu itens");

        SimpleContainer payer = new SimpleContainer(new ItemStack(Items.EMERALD, 7));
        Treasury treasury = new Treasury(helper.getLevel(), settlement);
        helper.assertTrue(treasury.depositFrom(payer, 3, EconomyTransactionType.TAX, 10L,
                UUID.randomUUID(), "teste"), "O depósito no tesouro falhou");
        SimpleContainer receiver = new SimpleContainer(3);
        helper.assertTrue(treasury.withdrawTo(receiver, 2, EconomyTransactionType.SALARY, 20L,
                UUID.randomUUID(), "teste"), "A retirada do tesouro falhou");
        helper.assertTrue(payer.countItem(Items.EMERALD) == 4 && treasury.balance() == 1
                        && receiver.countItem(Items.EMERALD) == 2,
                "A tesouraria não conservou a quantidade física de esmeraldas");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void taxUsesRegisteredIncomeOnly(GameTestHelper helper) {
        helper.assertTrue(DailyEconomyManager.taxDue(20) == 2, "O imposto deveria ser 10% da renda registrada");
        helper.assertTrue(DailyEconomyManager.taxDue(9) == 0, "Renda abaixo de dez não deveria gerar arredondamento para cima");
        helper.assertTrue(DailyEconomyManager.taxDue(0) == 0, "Saldo sem renda não deveria gerar imposto");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void constructionConsumesAtMostTwoBlocksPerCycle(GameTestHelper helper) {
        BlockPos relativeOrigin = new BlockPos(1, 1, 1);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            helper.setBlock(relativeOrigin.offset(x, -1, z), Blocks.STONE);
        }
        BlockPos relativeStorage = new BlockPos(4, 1, 1);
        helper.setBlock(relativeStorage, Blocks.BARREL);
        BlockPos storagePosition = helper.absolutePos(relativeStorage);
        Container barrel = (Container) helper.getLevel().getBlockEntity(storagePosition);
        helper.assertTrue(barrel != null, "O barril de materiais não foi criado");
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 9));
        barrel.setItem(1, new ItemStack(Items.OAK_PLANKS, 14));
        barrel.setItem(2, new ItemStack(Items.OAK_SLAB, 9));
        barrel.setItem(3, new ItemStack(Items.BARREL, 1));

        BlockPos origin = helper.absolutePos(relativeOrigin);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Obra", origin,
                helper.getLevel().dimension(), 64, helper.getLevel().getGameTime());
        settlement.addStorage(storagePosition);
        ConstructionProject project = new ConstructionProject(UUID.randomUUID(),
                ConstructionManager.SMALL_STORAGE_HUT, origin);
        project.setStage(ConstructionStage.WAITING_RESOURCES);
        settlement.addProject(project);

        ConstructionManager.WorkResult result = ConstructionManager.workProject(
                helper.getLevel(), settlement, null);
        helper.assertTrue(result == ConstructionManager.WorkResult.IN_PROGRESS,
                "A obra deveria iniciar com todos os materiais disponíveis");
        helper.assertTrue(project.blockIndex() > 0 && project.blockIndex() <= ConstructionManager.BLOCKS_PER_WORK_CYCLE,
                "A obra colocou blocos demais em um ciclo");
        helper.assertTrue(new SettlementStorage(helper.getLevel(), settlement).count(Items.COBBLESTONE) == 7,
                "Os blocos colocados não consumiram materiais físicos");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void managerLinksResidentsChoosesSuccessorAndRemovesSafely(GameTestHelper helper) {
        BlockPos bell = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos barrel = helper.absolutePos(new BlockPos(1, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.BELL);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.BARREL);
        ResidentEntity first = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 1));
        ResidentEntity second = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(3, 1, 2));
        helper.assertTrue(first.getPersonId() != null && second.getPersonId() != null,
                "Os habitantes de teste não receberam identidade");

        Settlement settlement = SettlementManager.create(helper.getLevel(), "Fundação", bell);
        helper.assertTrue(settlement.center().equals(bell), "O sino não foi usado como centro");
        helper.assertTrue(settlement.storagePositions().contains(barrel), "O barril próximo não foi registrado");
        helper.assertTrue(settlement.memberIds().contains(first.getPersonId())
                        && settlement.memberIds().contains(second.getPersonId()),
                "Habitantes próximos não foram vinculados");
        UUID oldLeader = settlement.leaderId();
        helper.assertTrue(oldLeader != null, "Nenhum líder adulto foi escolhido");
        ResidentEntity leader = oldLeader.equals(first.getPersonId()) ? first : second;
        ResidentEntity survivor = leader == first ? second : first;
        leader.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(survivor.getPersonId().equals(settlement.leaderId()),
                "O líder morto não foi substituído pelo membro vivo");
        helper.assertTrue(SettlementManager.remove(helper.getLevel(), settlement), "O povoado não foi removido");
        helper.assertTrue(survivor.getSettlementId() == null, "A remoção deixou vínculo inválido no habitante carregado");
        survivor.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void evolutionRequiresEveryVillageCondition(GameTestHelper helper) {
        BlockPos relativeStorage = new BlockPos(4, 1, 4);
        helper.setBlock(relativeStorage, Blocks.BARREL);
        BlockPos storagePosition = helper.absolutePos(relativeStorage);
        Container barrel = (Container) helper.getLevel().getBlockEntity(storagePosition);
        helper.assertTrue(barrel != null, "O armazém de evolução não foi criado");
        barrel.setItem(0, new ItemStack(Items.BREAD, 30));
        barrel.setItem(1, new ItemStack(Items.EMERALD, 16));

        Settlement settlement = new Settlement(UUID.randomUUID(), "Evolução", storagePosition,
                helper.getLevel().dimension(), 64, helper.getLevel().getGameTime());
        settlement.addStorage(storagePosition);
        List<ResidentEntity> residents = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ResidentEntity resident = helper.spawn(ModEntityTypes.RESIDENT.get(),
                    new BlockPos(1 + i % 3, 1, 1 + i / 3));
            residents.add(resident);
            resident.setSettlementId(settlement.id());
            settlement.addMember(resident.getPersonId(), helper.getLevel().getGameTime());
        }
        residents.get(0).getProfessionData().assign(NpcProfession.BLACKSMITH, storagePosition,
                helper.getLevel().dimension().location());
        residents.get(1).getProfessionData().assign(NpcProfession.COOK, storagePosition,
                helper.getLevel().dimension().location());
        helper.assertFalse(SettlementEvolutionManager.evaluate(helper.getLevel(), settlement),
                "O acampamento evoluiu sem concluir o depósito");

        ConstructionProject project = new ConstructionProject(UUID.randomUUID(),
                ConstructionManager.SMALL_STORAGE_HUT, helper.absolutePos(new BlockPos(1, 1, 3)));
        project.setStage(ConstructionStage.COMPLETED);
        settlement.addProject(project);
        helper.assertTrue(SettlementEvolutionManager.evaluate(helper.getLevel(), settlement),
                "O acampamento não evoluiu mesmo cumprindo todos os requisitos");
        helper.assertTrue(settlement.level() == SettlementLevel.VILLAGE, "O nível VILLAGE não foi salvo no estado");
        residents.forEach(ResidentEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void residentConsumesFoodAndDepositsProducedItem(GameTestHelper helper) {
        BlockPos relativeStorage = new BlockPos(2, 1, 2);
        helper.setBlock(relativeStorage, Blocks.BARREL);
        BlockPos storagePosition = helper.absolutePos(relativeStorage);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Necessidades", storagePosition,
                helper.getLevel().dimension(), 64, helper.getLevel().getGameTime());
        settlement.addStorage(storagePosition);
        ResidentEntity resident = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(1, 1, 1));
        resident.getNpcInventory().setItem(0, new ItemStack(Items.BREAD, 2));
        resident.getNpcInventory().setItem(1, new ItemStack(Items.IRON_SWORD, 1));
        resident.getNeeds().set(NeedType.HUNGER, 100);

        helper.assertTrue(NeedsManager.eatFromInventory(resident), "O habitante não consumiu comida real");
        helper.assertTrue(resident.getNpcInventory().countItem(Items.BREAD) == 1
                        && resident.getNeeds().hunger() < 100,
                "Comer não reduziu a pilha ou a fome corretamente");
        SettlementStorage storage = new SettlementStorage(helper.getLevel(), settlement);
        helper.assertTrue(storage.depositExcess(resident) == 1, "O produto não foi depositado no armazém");
        helper.assertTrue(storage.count(Items.IRON_SWORD) == 1
                        && resident.getNpcInventory().countItem(Items.BREAD) == 1,
                "O depósito perdeu o produto ou removeu a reserva de comida");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyIsBornOnlyInsideAValidPhysicalHome(GameTestHelper helper) {
        buildClosedHome(helper);
        ServerLevel level = helper.getLevel();
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        FamilyManager families = new FamilyManager(society);
        PersonData mother = families.createPerson("MaeTeste", null, Gender.FEMALE, LifeStage.ADULT);
        PersonData father = families.createPerson("PaiTeste", null, Gender.MALE, LifeStage.ADULT);
        HouseholdData household = families.marry(mother.getPersonId(), father.getPersonId(), null);

        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        Settlement settlement = new Settlement(UUID.randomUUID(), "Familias", center,
                level.dimension(), 16, level.getGameTime());
        settlement.addMember(mother.getPersonId(), level.getGameTime());
        settlement.addMember(father.getPersonId(), level.getGameTime());
        helper.assertTrue(HousingManager.refresh(level, settlement), "A casa fechada com cama nao foi reconhecida");
        helper.assertTrue(HousingManager.assignHouseholds(settlement, society), "A familia nao recebeu residencia");
        SettlementResidence home = settlement.residenceForHousehold(household.getHouseholdId()).orElse(null);
        helper.assertTrue(home != null, "Nenhuma residencia foi vinculada ao nucleo familiar");
        helper.assertTrue(home.capacity() == SettlementResidence.DEFAULT_CAPACITY + 2,
                "O berco nao ampliou a capacidade da residencia");

        ResidentEntity motherEntity = ModEntityTypes.RESIDENT.get().create(level);
        helper.assertTrue(motherEntity != null, "A entidade da mae nao foi criada");
        motherEntity.initializePerson(mother);
        motherEntity.setSettlementId(settlement.id());
        BlockPos outside = helper.absolutePos(new BlockPos(8, 1, 4));
        motherEntity.moveTo(outside.getX() + 0.5D, outside.getY(), outside.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(motherEntity), "A entidade da mae nao entrou no mundo");
        families.startPregnancy(mother.getPersonId(), father.getPersonId(),
                level.getGameTime() - ReproductionManager.PREGNANCY_TICKS, level.getGameTime());

        ReproductionManager.tick(level, settlement);
        helper.assertTrue(household.getChildrenIds().isEmpty(), "O bebe nasceu fora da casa");
        motherEntity.moveTo(home.interiorPosition().getX() + 0.5D, home.interiorPosition().getY(),
                home.interiorPosition().getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(ReproductionManager.tick(level, settlement), "O parto dentro da casa nao foi concluido");
        helper.assertTrue(household.getChildrenIds().size() == 1, "A genealogia nao recebeu exatamente um filho");
        PersonData child = society.getPerson(household.getChildrenIds().get(0)).orElse(null);
        helper.assertTrue(child != null && child.getLifeStage() == LifeStage.BABY
                        && father.getPersonId().equals(child.getFatherId())
                        && mother.getPersonId().equals(child.getMotherId()),
                "O bebe nao preservou fase de vida ou parentesco");
        helper.assertTrue(child.getEntityId() != null && level.getEntity(child.getEntityId()) instanceof ResidentEntity baby
                        && baby.isBaby() && HousingManager.isInside(home, baby.blockPosition())
                        && settlement.memberIds().contains(child.getPersonId()),
                "O bebe fisico nao nasceu dentro da residencia e vinculado ao povoado");
        level.getEntitiesOfClass(ResidentEntity.class,
                motherEntity.getBoundingBox().inflate(32.0D)).forEach(ResidentEntity::discard);
        helper.succeed();
    }

    private static void buildClosedHome(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) for (int z = 1; z <= 7; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 7; x++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(x, y, 7), Blocks.OAK_PLANKS);
            }
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(7, y, z), Blocks.OAK_PLANKS);
            }
        }
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(new BlockPos(4, 1, 5), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
        helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.CRADLE.get());
    }
}

package com.sam.realmfolk.gametest;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.content.ModBlocks;
import com.sam.realmfolk.content.ModItems;
import com.sam.realmfolk.entity.ModEntityTypes;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.integration.HostileMobIntegration;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.profession.forestry.ForestryManager;
import com.sam.realmfolk.profession.farming.FarmingManager;
import com.sam.realmfolk.profession.mining.MiningManager;
import com.sam.realmfolk.profession.cooking.CookingManager;
import com.sam.realmfolk.profession.fishing.FishingManager;
import com.sam.realmfolk.profession.hunting.HuntingManager;
import com.sam.realmfolk.profession.blacksmith.BlacksmithManager;
import com.sam.realmfolk.profession.merchant.MerchantManager;
import com.sam.realmfolk.profession.guard.GuardManager;
import com.sam.realmfolk.profession.ModProfessionItems;
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
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
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
    public static void farmerHarvestsAndReplantsCarrots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crop = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.GLOWSTONE);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Horta", crop,
                level.dimension(), 16, level.getGameTime());
        ResidentEntity farmer = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 3));
        farmer.getProfessionData().assign(NpcProfession.FARMER,
                helper.absolutePos(new BlockPos(1, 1, 1)), level.dimension().location());
        farmer.getNpcInventory().addItem(new ItemStack(Items.IRON_HOE));
        helper.runAfterDelay(2, () -> {
            helper.setBlock(new BlockPos(3, 1, 3), Blocks.CARROTS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CarrotBlock.AGE, 7));
            WorkOrder order = FarmingManager.createOrderAt(level, settlement, crop);
            helper.assertTrue(order != null, "A cenoura madura não gerou ordem agrícola");
            FarmingManager.WorkResult result = FarmingManager.workCrop(level, settlement, farmer, order);
            helper.assertTrue(result == FarmingManager.WorkResult.COMPLETED,
                    "O fazendeiro não colheu e replantou a cenoura: " + result);
            helper.assertTrue(level.getBlockState(crop).is(Blocks.CARROTS)
                            && level.getBlockState(crop).getValue(net.minecraft.world.level.block.CarrotBlock.AGE) == 0,
                    "A cenoura não foi replantada no primeiro estágio");
            helper.assertTrue(farmer.getNpcInventory().countItem(Items.CARROT) > 0,
                    "A colheita de cenoura não chegou ao inventário");
            farmer.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void cookPreparesStoredPotato(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos storagePosition = helper.absolutePos(new BlockPos(4, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.SMOKER);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.BARREL);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Cozinha da Horta", station,
                level.dimension(), 16, level.getGameTime());
        settlement.addStorage(storagePosition);
        Container barrel = (Container) level.getBlockEntity(storagePosition);
        helper.assertTrue(barrel != null, "O barril de batatas não foi criado");
        barrel.setItem(0, new ItemStack(Items.POTATO));
        ResidentEntity cook = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 1));
        cook.getProfessionData().assign(NpcProfession.COOK, station, level.dimension().location());
        cook.getNpcInventory().addItem(new ItemStack(ModItems.COOK_LADLE.get()));
        WorkOrder order = CookingManager.createOrderFor(level, settlement, station, Items.POTATO);
        helper.assertTrue(order != null && CookingManager.collectIngredients(level, settlement, cook, order),
                "A batata não foi retirada fisicamente do barril");
        helper.assertTrue(CookingManager.prepareMeal(level, settlement, cook, order)
                        == CookingManager.WorkResult.COMPLETED,
                "O cozinheiro não assou a batata");
        helper.assertTrue(cook.getNpcInventory().countItem(Items.BAKED_POTATO) == 1
                        && new SettlementStorage(level, settlement).count(Items.POTATO) == 0,
                "A receita de batata duplicou ou perdeu recursos");
        ProfessionService.remove(level, cook);
        cook.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blacksmithAnswersARealToolShortage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anvil = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos composter = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos storagePosition = helper.absolutePos(new BlockPos(5, 1, 4));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.ANVIL);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.COMPOSTER);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.BARREL);
        Container barrel = (Container) level.getBlockEntity(storagePosition);
        helper.assertTrue(barrel != null, "O armazém da ferraria não foi criado");
        barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        barrel.setItem(1, new ItemStack(Items.STICK, 2));
        Settlement settlement = new Settlement(UUID.randomUUID(), "Ferraria", anvil,
                level.dimension(), 16, level.getGameTime());
        settlement.addStorage(storagePosition);
        ResidentEntity smith = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(1, 1, 2));
        ResidentEntity farmer = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 2));
        smith.setSettlementId(settlement.id());
        farmer.setSettlementId(settlement.id());
        settlement.addMember(smith.getPersonId(), level.getGameTime());
        settlement.addMember(farmer.getPersonId(), level.getGameTime());
        smith.getProfessionData().assign(NpcProfession.BLACKSMITH, anvil, level.dimension().location());
        farmer.getProfessionData().assign(NpcProfession.FARMER, composter, level.dimension().location());
        smith.getNpcInventory().addItem(new ItemStack(ModProfessionItems.BLACKSMITH_HAMMER.get()));

        helper.assertTrue(BlacksmithManager.ensureWorkOrder(level, settlement),
                "A falta de enxada do fazendeiro não gerou uma ordem para o ferreiro");
        WorkOrder order = settlement.taskBoard().orders().get(0);
        helper.assertTrue(order.result() == Items.IRON_HOE && BlacksmithManager.isBlacksmithOrder(order),
                "A ordem da ferraria não escolheu a ferramenta ausente");
        SettlementStorage storage = new SettlementStorage(level, settlement);
        helper.assertTrue(storage.moveItemTo(Items.IRON_INGOT, 2, smith.getNpcInventory())
                        && storage.moveItemTo(Items.STICK, 2, smith.getNpcInventory()),
                "Os materiais físicos não chegaram à ferraria");
        helper.assertTrue(ProfessionService.produce(smith, Items.IRON_HOE)
                        != ProfessionService.ProductionResult.NO_RECIPE,
                "O ferreiro não produziu a ferramenta solicitada");
        helper.assertTrue(smith.getNpcInventory().countItem(Items.IRON_HOE) == 1
                        && storage.count(Items.IRON_INGOT) == 0 && storage.count(Items.STICK) == 0,
                "A ferramenta ou o consumo de materiais da ferraria está incorreto");
        ProfessionService.remove(level, smith);
        ProfessionService.remove(level, farmer);
        smith.discard();
        farmer.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void merchantRestocksFromAPhysicalBarrel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lectern = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos storagePosition = helper.absolutePos(new BlockPos(4, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.LECTERN);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.BARREL);
        Container barrel = (Container) level.getBlockEntity(storagePosition);
        helper.assertTrue(barrel != null, "O barril comercial não foi criado");
        barrel.setItem(0, new ItemStack(Items.BREAD, 8));
        Settlement settlement = new Settlement(UUID.randomUUID(), "Mercado", lectern,
                level.dimension(), 16, level.getGameTime());
        settlement.addStorage(storagePosition);
        ResidentEntity merchant = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 1));
        merchant.getProfessionData().assign(NpcProfession.MERCHANT, lectern, level.dimension().location());
        WorkOrder order = MerchantManager.createOrder(level, settlement, storagePosition, Items.BREAD, 8);
        helper.assertTrue(order != null && MerchantManager.canPerform(level, settlement, merchant, order),
                "O estoque físico não gerou uma ordem válida para o comerciante");
        helper.assertTrue(MerchantManager.reserveStock(level, settlement, order),
                "A mercadoria não foi reservada para a ordem comercial");
        helper.assertTrue(MerchantManager.restock(level, settlement, merchant, order)
                        == MerchantManager.WorkResult.COMPLETED,
                "O comerciante não retirou a mercadoria do armazém");
        helper.assertTrue(new SettlementStorage(level, settlement).count(Items.BREAD) == 0
                        && merchant.getTradeInventory().countItem(Items.BREAD) == 8,
                "O reabastecimento duplicou ou perdeu mercadorias");
        ProfessionService.remove(level, merchant);
        merchant.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardPatrolsARegisteredPhysicalMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos point = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BELL);
        helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.PATROL_MARKER.get());
        Settlement settlement = new Settlement(UUID.randomUUID(), "Muralha", point,
                level.dimension(), 16, level.getGameTime());
        settlement.addPatrolPosition(point);
        ResidentEntity guard = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(3, 1, 4));
        guard.getProfessionData().assign(NpcProfession.GUARD, station, level.dimension().location());
        guard.getNpcInventory().addItem(new ItemStack(Items.IRON_SWORD));
        WorkOrder order = GuardManager.createOrderAt(level, settlement, point);
        helper.assertTrue(order != null && GuardManager.canPerform(level, settlement, guard, order),
                "O marcador registrado não gerou uma patrulha válida");
        helper.assertTrue(GuardManager.patrol(level, settlement, guard, order)
                        == GuardManager.WorkResult.COMPLETED,
                "O guarda não verificou o ponto de patrulha");
        ProfessionService.remove(level, guard);
        guard.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hunterFiresARealArrowAtASafePopulation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos storagePosition = helper.absolutePos(new BlockPos(2, 1, 5));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FLETCHING_TABLE);
        helper.setBlock(new BlockPos(2, 1, 5), Blocks.BARREL);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Campo de Caça",
                helper.absolutePos(new BlockPos(4, 1, 4)), level.dimension(), 20, level.getGameTime());
        settlement.addStorage(storagePosition);
        Container arrowBarrel = (Container) level.getBlockEntity(storagePosition);
        helper.assertTrue(arrowBarrel != null, "O barril de flechas não foi criado");
        arrowBarrel.setItem(0, new ItemStack(Items.ARROW, HuntingManager.ARROWS_PER_ORDER));
        List<Cow> herd = new ArrayList<>();
        for (int i = 0; i < HuntingManager.MIN_POPULATION; i++) {
            herd.add(spawnWildCow(helper, new BlockPos(5 + i, 1, 3)));
        }
        ResidentEntity hunter = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(3, 1, 3));
        hunter.getProfessionData().assign(NpcProfession.HUNTER, station, level.dimension().location());
        hunter.getNpcInventory().addItem(new ItemStack(Items.BOW));

        WorkOrder order = HuntingManager.createOrderFor(level, settlement, herd.get(0));
        helper.assertTrue(order != null, "Uma população segura não gerou ordem de caça");
        helper.assertTrue(HuntingManager.collectAmmunition(level, settlement, hunter, order),
                "O caçador não retirou as flechas físicas do armazém");
        helper.assertTrue(new SettlementStorage(level, settlement).count(Items.ARROW) == 0
                        && hunter.getNpcInventory().countItem(Items.ARROW) == HuntingManager.ARROWS_PER_ORDER,
                "A transferência de flechas duplicou ou perdeu munição");
        helper.assertTrue(ProfessionService.produce(hunter) == ProfessionService.ProductionResult.NO_RECIPE,
                "O caminho abstrato do caçador ainda transforma flechas em recursos");
        helper.assertTrue(HuntingManager.hunt(level, settlement, hunter, order)
                        == HuntingManager.WorkResult.IN_PROGRESS,
                "O caçador não iniciou o ataque físico");
        helper.assertTrue(hunter.getNpcInventory().countItem(Items.ARROW)
                        == HuntingManager.ARROWS_PER_ORDER - 1,
                "O disparo não consumiu uma flecha real");
        helper.assertTrue(hunter.getNpcInventory().getItem(0).getDamageValue() == 1,
                "O arco não perdeu durabilidade");
        helper.assertTrue(!level.getEntitiesOfClass(Arrow.class,
                        hunter.getBoundingBox().inflate(16.0D)).isEmpty(),
                "Nenhum projétil real foi criado");
        herd.get(0).discard();
        hunter.setTarget(null);
        level.addFreshEntity(new ItemEntity(level, hunter.getX(), hunter.getY(), hunter.getZ(),
                new ItemStack(Items.BEEF, 2)));
        helper.assertTrue(HuntingManager.hunt(level, settlement, hunter, order)
                        == HuntingManager.WorkResult.COMPLETED,
                "O caçador não recolheu os drops físicos depois de perder a referência do alvo morto");
        helper.assertTrue(hunter.getNpcInventory().countItem(Items.BEEF) == 2,
                "Os drops da caça não chegaram ao inventário do caçador");
        ProfessionService.remove(level, hunter);
        hunter.discard();
        for (Cow cow : herd) cow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hunterProtectsSmallPopulationsAndYoungAnimals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Settlement settlement = new Settlement(UUID.randomUUID(), "Reserva",
                helper.absolutePos(new BlockPos(4, 1, 4)), level.dimension(), 20, level.getGameTime());
        List<Cow> herd = new ArrayList<>();
        for (int i = 0; i < HuntingManager.MIN_POPULATION - 1; i++) {
            herd.add(spawnWildCow(helper, new BlockPos(4 + i, 1, 3)));
        }
        Cow calf = spawnWildCow(helper, new BlockPos(8, 1, 3));
        calf.setAge(-24000);
        helper.assertTrue(HuntingManager.createOrderFor(level, settlement, herd.get(0)) == null,
                "O filhote foi contado como adulto e permitiu reduzir demais o rebanho");
        herd.add(spawnWildCow(helper, new BlockPos(9, 1, 3)));
        helper.assertTrue(HuntingManager.createOrderFor(level, settlement, calf) == null,
                "Um filhote foi escolhido como alvo de caça");
        for (Cow cow : herd) cow.discard();
        calf.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fishermanUsesRealWaterAndVanillaFishingLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos bank = helper.absolutePos(new BlockPos(2, 1, 3));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BARREL);
        helper.setBlock(new BlockPos(2, 0, 3), Blocks.STONE);
        for (int x = 3; x <= 5; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
            }
        }
        Settlement settlement = new Settlement(UUID.randomUUID(), "Vila dos Pescadores", bank,
                level.dimension(), 16, level.getGameTime());
        ResidentEntity fisherman = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 2));
        fisherman.getProfessionData().assign(NpcProfession.FISHERMAN, station, level.dimension().location());
        fisherman.getNpcInventory().addItem(new ItemStack(Items.FISHING_ROD));

        WorkOrder order = FishingManager.createOrderAt(level, settlement, bank);
        helper.assertTrue(order != null, "A margem com água real não gerou ordem de pesca");
        helper.assertTrue(ProfessionService.produce(fisherman) == ProfessionService.ProductionResult.NO_RECIPE,
                "O caminho abstrato do pescador ainda cria peixes");
        int before = inventoryItemCount(fisherman.getNpcInventory());
        helper.assertTrue(FishingManager.fish(level, settlement, fisherman, order)
                        == FishingManager.WorkResult.COMPLETED,
                "O pescador não obteve loot pela tabela vanilla de pesca");
        helper.assertTrue(inventoryItemCount(fisherman.getNpcInventory()) > before,
                "A captura física não chegou ao inventário do pescador");
        helper.assertTrue(fisherman.getNpcInventory().getItem(0).getDamageValue() == 1,
                "A vara de pesca não perdeu durabilidade");
        fisherman.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fishermanRejectsAnArtificialSingleWaterBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bank = helper.absolutePos(new BlockPos(2, 1, 3));
        helper.setBlock(new BlockPos(2, 0, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Poça", bank,
                level.dimension(), 16, level.getGameTime());
        helper.assertTrue(FishingManager.createOrderAt(level, settlement, bank) == null,
                "Uma poça de um bloco foi aceita como ponto de pesca");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookTurnsStoredWheatIntoDepositableBread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos storagePosition = helper.absolutePos(new BlockPos(5, 1, 4));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.SMOKER);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.BARREL);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Cozinha", station,
                level.dimension(), 16, level.getGameTime());
        settlement.addStorage(storagePosition);
        Container barrel = (Container) level.getBlockEntity(storagePosition);
        helper.assertTrue(barrel != null, "O barril da cozinha não foi criado");
        barrel.setItem(0, new ItemStack(Items.WHEAT, 6));
        ResidentEntity cook = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 1));
        cook.getProfessionData().assign(NpcProfession.COOK, station, level.dimension().location());
        cook.getNpcInventory().addItem(new ItemStack(ModItems.COOK_LADLE.get()));

        WorkOrder order = CookingManager.createOrderAt(level, settlement, station);
        helper.assertTrue(order != null, "O trigo armazenado não gerou ordem de cozinha");
        helper.assertTrue(ProfessionService.produce(cook) == ProfessionService.ProductionResult.NO_RECIPE,
                "O caminho abstrato do cozinheiro ainda produz comida");
        helper.assertTrue(CookingManager.collectIngredients(level, settlement, cook, order),
                "O cozinheiro não retirou o trigo físico do armazém");
        helper.assertTrue(new SettlementStorage(level, settlement).count(Items.WHEAT) == 3
                        && cook.getNpcInventory().countItem(Items.WHEAT) == CookingManager.WHEAT_PER_BREAD,
                "A transferência do trigo duplicou ou perdeu ingredientes");
        helper.assertTrue(CookingManager.prepareMeal(level, settlement, cook, order)
                        == CookingManager.WorkResult.COMPLETED,
                "O cozinheiro não preparou pão com ingredientes físicos");
        helper.assertTrue(new SettlementStorage(level, settlement).count(Items.WHEAT) == 3,
                "A cozinha não consumiu exatamente três trigos reais");
        helper.assertTrue(cook.getNpcInventory().countItem(Items.BREAD) == 1,
                "O pão produzido não chegou ao inventário do cozinheiro");
        helper.assertTrue(cook.getNpcInventory().getItem(0).getDamageValue() == 1,
                "O utensílio não perdeu durabilidade");

        cook.getNpcInventory().addItem(new ItemStack(Items.BREAD, 4));
        SettlementStorage storage = new SettlementStorage(level, settlement);
        helper.assertTrue(storage.depositExcess(cook) == 3,
                "O excedente de comida não foi depositado");
        helper.assertTrue(storage.count(Items.BREAD) == 3
                        && cook.getNpcInventory().countItem(Items.BREAD) == SettlementStorage.PERSONAL_FOOD_RESERVE,
                "A reserva pessoal de comida não foi preservada");
        ProfessionService.remove(level, cook);
        cook.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minerBreaksARealAuthorizedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos marker = helper.absolutePos(new BlockPos(2, 1, 4));
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 4));
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BLAST_FURNACE);
        helper.setBlock(new BlockPos(2, 1, 4), ModBlocks.WORK_MARKER.get());
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 0, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Pedreira", target,
                level.dimension(), 16, level.getGameTime());
        settlement.addWorkPosition(marker);
        ResidentEntity miner = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(3, 1, 4));
        miner.getProfessionData().assign(NpcProfession.MINER, station, level.dimension().location());
        miner.getNpcInventory().addItem(new ItemStack(Items.IRON_PICKAXE));

        WorkOrder order = MiningManager.createOrderAt(level, settlement, target);
        helper.assertTrue(order != null, "O bloco autorizado não gerou uma ordem física de mineração");
        helper.assertTrue(ProfessionService.produce(miner) == ProfessionService.ProductionResult.NO_RECIPE,
                "O caminho abstrato do minerador ainda produz recursos");
        helper.assertTrue(MiningManager.workBlock(level, settlement, miner, order)
                        == MiningManager.WorkResult.COMPLETED,
                "O minerador não concluiu a quebra do bloco físico");
        helper.assertTrue(level.getBlockState(target).isAir(), "O bloco físico não foi removido");
        helper.assertTrue(miner.getNpcInventory().countItem(Items.COBBLESTONE) == 1,
                "O drop real de pedregulho não chegou ao inventário");
        helper.assertTrue(miner.getNpcInventory().getItem(0).getDamageValue() == 1,
                "A picareta não perdeu durabilidade");
        miner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minerRejectsBlocksWithoutAWorkMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 4));
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 0, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Pedreira", target,
                level.dimension(), 16, level.getGameTime());
        helper.assertTrue(MiningManager.createOrderAt(level, settlement, target) == null,
                "O minerador tentou quebrar um bloco fora de uma área autorizada");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farmerHarvestsAndReplantsRealWheat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cropPosition = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.GLOWSTONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.COMPOSTER);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Plantação", cropPosition,
                level.dimension(), 16, level.getGameTime());
        ResidentEntity farmer = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 4));
        farmer.getProfessionData().assign(NpcProfession.FARMER, station, level.dimension().location());
        farmer.getNpcInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getNpcInventory().addItem(new ItemStack(Items.WHEAT_SEEDS));

        helper.runAfterDelay(2, () -> {
            helper.setBlock(new BlockPos(4, 1, 4), wheat.getStateForAge(wheat.getMaxAge()));
            WorkOrder order = FarmingManager.createOrderAt(level, settlement, cropPosition);
            helper.assertTrue(order != null, "O trigo maduro não gerou uma ordem física");
            helper.assertTrue(ProfessionService.produce(farmer) == ProfessionService.ProductionResult.NO_RECIPE,
                    "O caminho abstrato do fazendeiro ainda produz trigo");
            FarmingManager.WorkResult farmingResult = FarmingManager.workCrop(level, settlement, farmer, order);
            helper.assertTrue(farmingResult == FarmingManager.WorkResult.COMPLETED,
                    "O fazendeiro não concluiu a colheita física: " + farmingResult);
            helper.assertTrue(level.getBlockState(cropPosition).is(Blocks.WHEAT)
                            && wheat.getAge(level.getBlockState(cropPosition)) == 0,
                    "O trigo não foi replantado no estágio inicial");
            helper.assertTrue(farmer.getNpcInventory().countItem(Items.WHEAT) > 0,
                    "A colheita real não entregou trigo ao inventário");
            helper.assertTrue(farmer.getNpcInventory().getItem(0).getDamageValue() == 1,
                    "A enxada não perdeu durabilidade na colheita");
            farmer.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void farmerRejectsImmatureWheat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cropPosition = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.WHEAT);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Plantação", cropPosition,
                level.dimension(), 16, level.getGameTime());
        helper.assertTrue(FarmingManager.createOrderAt(level, settlement, cropPosition) == null,
                "O fazendeiro tentou colher trigo ainda imaturo");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lumberjackHarvestsRealLogsAndReplants(GameTestHelper helper) {
        buildOakTree(helper, false);
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos station = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CRAFTING_TABLE);
        Settlement settlement = new Settlement(UUID.randomUUID(), "Bosque", base,
                level.dimension(), 16, level.getGameTime());
        ResidentEntity lumberjack = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(2, 1, 4));
        lumberjack.getProfessionData().assign(NpcProfession.LUMBERJACK, station, level.dimension().location());
        lumberjack.getNpcInventory().addItem(new ItemStack(Items.IRON_AXE));
        lumberjack.getNpcInventory().addItem(new ItemStack(Items.OAK_SAPLING));

        WorkOrder order = ForestryManager.createOrderAt(level, settlement, base);
        helper.assertTrue(order != null, "A árvore natural não gerou uma ordem física");
        helper.assertTrue(ProfessionService.produce(lumberjack) == ProfessionService.ProductionResult.NO_RECIPE,
                "O caminho abstrato do lenhador ainda produz madeira");
        ForestryManager.WorkResult result = ForestryManager.WorkResult.IN_PROGRESS;
        int steps = 0;
        while (result == ForestryManager.WorkResult.IN_PROGRESS && steps++ < 8) {
            result = ForestryManager.workTree(level, settlement, lumberjack, order);
        }
        helper.assertTrue(result == ForestryManager.WorkResult.COMPLETED,
                "O lenhador não concluiu o corte gradual da árvore");
        helper.assertTrue(lumberjack.getNpcInventory().countItem(Items.OAK_LOG) == 6,
                "As toras físicas não foram recolhidas exatamente uma vez");
        helper.assertTrue(level.getBlockState(base).is(Blocks.OAK_SAPLING),
                "A muda física disponível não foi replantada");
        helper.assertTrue(lumberjack.getNpcInventory().getItem(0).getDamageValue() == 6,
                "O machado não perdeu durabilidade por cada tronco cortado");
        lumberjack.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lumberjackRejectsLogsAttachedToAConstruction(GameTestHelper helper) {
        buildOakTree(helper, true);
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 1, 4));
        Settlement settlement = new Settlement(UUID.randomUUID(), "Proteção", base,
                level.dimension(), 16, level.getGameTime());
        helper.assertTrue(ForestryManager.inspectTree(level, settlement, base) == null,
                "O lenhador confundiu uma estrutura de madeira com árvore natural");
        helper.succeed();
    }

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
        BlockPos builderStation = helper.absolutePos(new BlockPos(5, 1, 1));
        helper.setBlock(new BlockPos(5, 1, 1), Blocks.STONECUTTER);
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

        ResidentEntity builder = helper.spawn(ModEntityTypes.RESIDENT.get(), new BlockPos(4, 1, 3));
        builder.getProfessionData().assign(NpcProfession.BUILDER, builderStation,
                helper.getLevel().dimension().location());
        builder.getNpcInventory().addItem(new ItemStack(ModItems.BUILDER_HAMMER.get()));
        ConstructionManager.WorkResult result = ConstructionManager.workProject(
                helper.getLevel(), settlement, builder);
        helper.assertTrue(result == ConstructionManager.WorkResult.IN_PROGRESS,
                "A obra deveria iniciar com todos os materiais disponíveis");
        helper.assertTrue(project.blockIndex() > 0 && project.blockIndex() <= ConstructionManager.BLOCKS_PER_WORK_CYCLE,
                "A obra colocou blocos demais em um ciclo");
        helper.assertTrue(new SettlementStorage(helper.getLevel(), settlement).count(Items.COBBLESTONE) == 7,
                "Os blocos colocados não consumiram materiais físicos");
        helper.assertTrue(builder.getNpcInventory().getItem(0).getDamageValue() == 2,
                "O martelo do construtor não perdeu durabilidade por bloco colocado");
        builder.discard();
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

    private static int inventoryItemCount(Container inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) count += inventory.getItem(slot).getCount();
        return count;
    }

    private static Cow spawnWildCow(GameTestHelper helper, BlockPos relativePosition) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) throw new IllegalStateException("Não foi possível criar a vaca de teste");
        BlockPos position = helper.absolutePos(relativePosition);
        cow.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
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

    private static void buildOakTree(GameTestHelper helper, boolean attachPlanks) {
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.DIRT);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 5, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 6, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(3, 6, 4), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(5, 6, 4), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(4, 6, 3), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(4, 6, 5), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(4, 7, 4), Blocks.OAK_LEAVES);
        if (attachPlanks) helper.setBlock(new BlockPos(5, 1, 4), Blocks.OAK_PLANKS);
    }
}

package com.sam.realmfolk.content.item;

import com.sam.realmfolk.client.menu.FamilyTreeMenu;
import com.sam.realmfolk.client.menu.ProfessionMenu;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.FamilyManager;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.economy.Treasury;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.housing.HousingManager;
import com.sam.realmfolk.society.needs.NeedType;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementManager;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RealmfolkUtilityItem extends Item {
    private final UtilityAction action;

    public RealmfolkUtilityItem(Properties properties, UtilityAction action) {
        super(properties);
        this.action = action;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level rawLevel, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(rawLevel instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        Settlement settlement = current(level, player.blockPosition());
        switch (action) {
            case SETTLEMENT_CHARTER, SETTLEMENT_LEDGER, FAMILY_REGISTER, RESIDENCE_DEED,
                    CONSTRUCTION_BLUEPRINT, WORK_CONTRACT, WORK_ORDER_BOOK, FAMILY_CERTIFICATE ->
                    DocumentContent.open(action, level, serverPlayer, stack, settlement);
            case LEADERS_SEAL -> useSeal(level, player, stack, settlement);
            case GUARD_HORN -> soundAlarm(level, player, stack, settlement);
            default -> { return InteractionResultHolder.pass(stack); }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        switch (action) {
            case SETTLEMENT_CHARTER -> foundSettlement(level, player, stack, clicked);
            case RESIDENCE_DEED -> assignDeed(level, player, stack, clicked);
            case SURVEYORS_ROD -> survey(level, player, stack, clicked.above());
            case CONSTRUCTION_BLUEPRINT -> plan(level, player, stack, clicked.above());
            case BUILDER_HAMMER -> helpBuild(level, player, stack, context.getHand());
            default -> { return InteractionResult.PASS; }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof ResidentEntity resident)) {
            return InteractionResult.PASS;
        }
        ServerLevel level = serverPlayer.serverLevel();
        PersonData person = resident.getPerson(level).orElse(null);
        if (person == null) return InteractionResult.PASS;
        switch (action) {
            case FAMILY_REGISTER -> FamilyTreeMenu.open(serverPlayer, resident, person.getPersonId(), 0);
            case RESIDENCE_DEED -> bindPerson(stack, person, "HouseholdId", person.getHouseholdId(), player,
                    "Familia selecionada: ");
            case WORK_CONTRACT -> ProfessionMenu.open(serverPlayer, resident,
                    resident.getProfessionData().profession() == NpcProfession.NONE
                            ? NpcProfession.FARMER : resident.getProfessionData().profession());
            case GUARD_BADGE -> message(player, ProfessionService.assign(serverPlayer, resident, NpcProfession.GUARD).message());
            case HEALER_SATCHEL -> heal(level, serverPlayer, resident, stack, hand);
            case WEDDING_RING -> wedding(level, serverPlayer, resident, person, stack);
            case SIMPLE_GIFT -> gift(level, serverPlayer, resident, person, stack, 3, false);
            case WOODEN_TOY -> gift(level, serverPlayer, resident, person, stack, 2, true);
            case FAMILY_CERTIFICATE -> {
                stack.getOrCreateTag().putUUID("PersonId", person.getPersonId());
                stack.setHoverName(Component.literal("Certidao de " + person.getDisplayName()));
                FamilyTreeMenu.open(serverPlayer, resident, person.getPersonId(), 0);
            }
            case LEADERS_SEAL -> bindLeader(level, player, resident, person, stack);
            default -> { return InteractionResult.PASS; }
        }
        return InteractionResult.CONSUME;
    }

    private static void foundSettlement(ServerLevel level, Player player, ItemStack stack, BlockPos origin) {
        BlockPos bell = SettlementManager.findNearestBell(level, origin).orElse(null);
        if (bell == null) { message(player, "Nenhum sino encontrado em ate 16 blocos."); return; }
        if (SettlementSavedData.get(level.getServer()).at(level.dimension(), bell).isPresent()) {
            message(player, "Este sino ja pertence a um povoado."); return;
        }
        Settlement settlement = SettlementManager.create(level, "Povoado de " + player.getName().getString(), bell);
        message(player, "Povoado " + settlement.name() + " fundado com " + settlement.memberIds().size() + " habitantes.");
        consumeOne(player, stack);
    }

    private static void showSettlement(ServerLevel level, Player player, @Nullable Settlement settlement) {
        if (settlement == null) { message(player, "Voce nao esta dentro de um povoado."); return; }
        SettlementStorage storage = new SettlementStorage(level, settlement);
        int treasury = new Treasury(level, settlement).balance();
        message(player, settlement.name() + " [" + settlement.level() + "] | habitantes "
                + settlement.memberIds().size() + " | comida " + storage.countFood() + " | tesouro "
                + treasury + " | tarefas " + settlement.taskBoard().orders().size() + " | projetos "
                + settlement.projects().size() + " | casas " + settlement.residences().size());
    }

    private static void showOrders(Player player, @Nullable Settlement settlement) {
        if (settlement == null) { message(player, "Voce nao esta dentro de um povoado."); return; }
        String orders = settlement.taskBoard().orders().stream().limit(8)
                .map(order -> order.type() + "=" + order.status())
                .reduce((a, b) -> a + " | " + b).orElse("nenhuma");
        message(player, "Ordens de " + settlement.name() + ": " + orders);
    }

    private static void assignDeed(ServerLevel level, Player player, ItemStack stack, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof BedBlock)) { message(player, "Use a escritura em uma cama valida."); return; }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID("HouseholdId")) { message(player, "Primeiro use a escritura em um membro da familia."); return; }
        Settlement settlement = current(level, clicked);
        if (settlement == null) { message(player, "A cama precisa estar dentro de um povoado."); return; }
        HousingManager.refresh(level, settlement);
        boolean assigned = HousingManager.assignResidence(settlement, HumanSocietySavedData.get(level),
                tag.getUUID("HouseholdId"), clicked);
        if (assigned) {
            SettlementSavedData.get(level.getServer()).changed();
            message(player, "Residencia atribuida a familia.");
            stack.removeTagKey("HouseholdId");
        } else message(player, "Esta cama nao pertence a uma residencia fisica valida.");
    }

    private static void survey(ServerLevel level, Player player, ItemStack stack, BlockPos origin) {
        Settlement settlement = current(level, origin);
        if (settlement == null) { message(player, "A area fica fora de um povoado."); return; }
        boolean safe = ConstructionManager.canPlanAt(level, settlement, ConstructionManager.SMALL_STORAGE_HUT, origin);
        if (safe) {
            stack.getOrCreateTag().putLong("SurveyPos", origin.asLong());
            stack.getOrCreateTag().putString("SurveyDimension", level.dimension().location().toString());
        }
        message(player, safe ? "Area segura registrada em " + origin.toShortString() + "." : "Area invalida para o projeto atual.");
    }

    private static void plan(ServerLevel level, Player player, ItemStack stack, BlockPos clickedOrigin) {
        BlockPos origin = clickedOrigin;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("SurveyPos")
                && level.dimension().location().toString().equals(tag.getString("SurveyDimension"))) {
            origin = BlockPos.of(tag.getLong("SurveyPos"));
        }
        Settlement settlement = current(level, origin);
        if (settlement == null) { message(player, "O projeto precisa ficar dentro de um povoado."); return; }
        ConstructionManager.PlanResult result = ConstructionManager.planProjectAt(level, settlement,
                ConstructionManager.SMALL_STORAGE_HUT, origin);
        message(player, result == ConstructionManager.PlanResult.SUCCESS
                ? "Projeto small_storage_hut criado." : "Projeto recusado: " + result.name() + ".");
        if (result == ConstructionManager.PlanResult.SUCCESS) consumeOne(player, stack);
    }

    private static void helpBuild(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
        Settlement settlement = current(level, player.blockPosition());
        if (settlement == null) { message(player, "Nenhum povoado encontrado."); return; }
        ConstructionManager.WorkResult result = ConstructionManager.workProject(level, settlement, null);
        message(player, "Trabalho de construcao: " + result.name() + ".");
        if (result == ConstructionManager.WorkResult.IN_PROGRESS || result == ConstructionManager.WorkResult.COMPLETED) {
            damage(stack, player, hand);
        }
    }

    private static void soundAlarm(ServerLevel level, Player player, ItemStack stack, @Nullable Settlement settlement) {
        if (settlement == null) { message(player, "A corneta so funciona dentro de um povoado."); return; }
        long now = level.getGameTime();
        settlement.government().setPriority(SettlementPolicy.DEFENSE, 100);
        settlement.taskBoard().add(new WorkOrder(UUID.randomUUID(), WorkOrderType.GUARD, 100, settlement.id(),
                player.blockPosition(), level.dimension(), Items.AIR, 0, Items.AIR, 0, now, now + 2400L));
        SettlementSavedData.get(level.getServer()).changed();
        level.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(),
                player.getSoundSource(), 2.0F, 1.0F);
        player.getCooldowns().addCooldown(stack.getItem(), 200);
        message(player, "Alarme emitido. Guardas receberam prioridade maxima.");
    }

    private static void bindLeader(ServerLevel level, Player player, ResidentEntity resident,
                                   PersonData person, ItemStack stack) {
        Settlement settlement = resident.getSettlementId() == null ? null
                : SettlementSavedData.get(level.getServer()).get(resident.getSettlementId()).orElse(null);
        if (settlement == null || !person.getPersonId().equals(settlement.leaderId())) {
            message(player, "O selo so pode ser autenticado pelo lider do povoado."); return;
        }
        stack.getOrCreateTag().putUUID("SettlementId", settlement.id());
        stack.getOrCreateTag().putInt("Policy", 0);
        message(player, "Selo autenticado por " + resident.getName().getString() + ".");
    }

    private static void useSeal(ServerLevel level, Player player, ItemStack stack, @Nullable Settlement nearby) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID("SettlementId")) { message(player, "Autentique o selo usando-o no lider do povoado."); return; }
        Settlement settlement = SettlementSavedData.get(level.getServer()).get(tag.getUUID("SettlementId")).orElse(null);
        if (settlement == null || nearby == null || !nearby.id().equals(settlement.id())) { message(player, "O selo nao pertence a este povoado."); return; }
        SettlementPolicy[] policies = SettlementPolicy.values();
        int selected = Math.floorMod(tag.getInt("Policy"), policies.length);
        if (player.isShiftKeyDown()) {
            selected = (selected + 1) % policies.length;
            tag.putInt("Policy", selected);
            message(player, "Politica selecionada: " + policies[selected] + ".");
            return;
        }
        SettlementPolicy policy = policies[selected];
        settlement.government().setPriority(policy, Math.min(100, settlement.government().priority(policy) + 10));
        SettlementSavedData.get(level.getServer()).changed();
        message(player, policy + " agora possui prioridade " + settlement.government().priority(policy) + ".");
    }

    private static void heal(ServerLevel level, ServerPlayer player, ResidentEntity resident,
                             ItemStack satchel, InteractionHand hand) {
        if (!removeOne(player, Items.HONEY_BOTTLE)) { message(player, "A bolsa precisa de um frasco de mel."); return; }
        resident.heal(8.0F);
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        damage(satchel, player, hand);
        message(player, resident.getName().getString() + " recebeu tratamento.");
    }

    private static void wedding(ServerLevel level, ServerPlayer player, ResidentEntity resident,
                                PersonData person, ItemStack ring) {
        if (person.getLifeStage() != LifeStage.ADULT || person.getSpouseId() != null) {
            message(player, "Somente adultos solteiros podem ser selecionados."); return;
        }
        CompoundTag tag = ring.getOrCreateTag();
        if (!tag.hasUUID("FirstPerson")) {
            tag.putUUID("FirstPerson", person.getPersonId());
            message(player, person.getDisplayName() + " foi selecionado para o casamento.");
            return;
        }
        UUID firstId = tag.getUUID("FirstPerson");
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        PersonData first = society.getPerson(firstId).orElse(null);
        if (first == null || first.getPersonId().equals(person.getPersonId())) { message(player, "Selecione duas pessoas diferentes."); return; }
        Settlement settlement = resident.getSettlementId() == null ? null
                : SettlementSavedData.get(level.getServer()).get(resident.getSettlementId()).orElse(null);
        if (settlement == null || !settlement.memberIds().contains(firstId)
                || settlement.residences().stream().noneMatch(home -> home.householdId() == null)) {
            message(player, "O casal precisa pertencer ao mesmo povoado e ter uma residencia livre."); return;
        }
        try {
            new FamilyManager(society).marry(firstId, person.getPersonId(),
                    first.getHouseId() != null ? first.getHouseId() : person.getHouseId());
            HousingManager.assignHouseholds(settlement, society);
            SettlementSavedData.get(level.getServer()).changed();
            message(player, first.getDisplayName() + " e " + person.getDisplayName() + " agora sao casados.");
            consumeOne(player, ring);
        } catch (IllegalArgumentException exception) {
            message(player, exception.getMessage());
        }
    }

    private static void gift(ServerLevel level, ServerPlayer player, ResidentEntity resident,
                             PersonData person, ItemStack stack, int affinity, boolean childrenOnly) {
        if (childrenOnly && person.getLifeStage() != LifeStage.BABY && person.getLifeStage() != LifeStage.CHILD) {
            message(player, "Este brinquedo e destinado a bebes e criancas."); return;
        }
        String key = "RealmfolkGift_" + player.getUUID();
        long day = level.getDayTime() / 24000L;
        if (resident.getPersistentData().getLong(key) == day + 1L) { message(player, "Este habitante ja recebeu um presente hoje."); return; }
        resident.getPersistentData().putLong(key, day + 1L);
        new FamilyManager(HumanSocietySavedData.get(level)).changeAffinity(person.getPersonId(), player.getUUID(), affinity);
        if (childrenOnly) resident.getNeeds().change(NeedType.REST, -20);
        consumeOne(player, stack);
        message(player, person.getDisplayName() + " gostou do presente.");
    }

    private static void openBoundCertificate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID("PersonId")) { message(player, "Use a certidao em um habitante para registra-la."); return; }
        PersonData person = HumanSocietySavedData.get(level).getPerson(tag.getUUID("PersonId")).orElse(null);
        if (person == null || person.getEntityId() == null || !(level.getEntity(person.getEntityId()) instanceof ResidentEntity resident)
                || resident.distanceToSqr(player) > 64.0D) {
            message(player, "O titular precisa estar carregado e proximo."); return;
        }
        FamilyTreeMenu.open(player, resident, person.getPersonId(), 0);
    }

    private static void bindPerson(ItemStack stack, PersonData person, String key, @Nullable UUID value,
                                   Player player, String prefix) {
        if (value == null) { message(player, "Este habitante ainda nao pertence a uma familia."); return; }
        stack.getOrCreateTag().putUUID(key, value);
        message(player, prefix + person.getDisplayName() + ".");
    }

    @Nullable
    private static Settlement current(ServerLevel level, BlockPos position) {
        return SettlementSavedData.get(level.getServer()).at(level.dimension(), position).orElse(null);
    }

    private static void consumeOne(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    private static boolean removeOne(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private static void damage(ItemStack stack, Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild || !stack.isDamageableItem()) return;
        stack.hurtAndBreak(1, player, value -> value.broadcastBreakEvent(hand));
    }

    private static void message(Player player, String text) { player.sendSystemMessage(Component.literal(text)); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realmfolk."
                + action.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY));
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.hasUUID("PersonId")) tooltip.add(Component.literal("Registro pessoal vinculado").withStyle(ChatFormatting.GRAY));
            if (tag.hasUUID("HouseholdId")) tooltip.add(Component.literal("Familia selecionada").withStyle(ChatFormatting.GRAY));
            if (tag.contains("SurveyPos")) tooltip.add(Component.literal("Area: " + BlockPos.of(tag.getLong("SurveyPos")).toShortString()).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}

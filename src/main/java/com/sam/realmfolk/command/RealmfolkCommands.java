package com.sam.realmfolk.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.construction.ConstructionProject;
import com.sam.realmfolk.society.economy.Treasury;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementManager;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.housing.HousingManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;

public final class RealmfolkCommands {
    private RealmfolkCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> settlement = Commands.literal("settlement");
        settlement.then(Commands.literal("create").requires(RealmfolkCommands::canDevelop)
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(RealmfolkCommands::create)));
        settlement.then(Commands.literal("info").executes(context -> showInfo(context.getSource())));
        settlement.then(Commands.literal("debug").executes(context -> showDebug(context.getSource())));
        settlement.then(Commands.literal("members").executes(context -> showMembers(context.getSource())));
        settlement.then(Commands.literal("tasks").executes(context -> showTasks(context.getSource())));
        settlement.then(Commands.literal("economy").executes(context -> showEconomy(context.getSource())));
        settlement.then(Commands.literal("projects").executes(context -> showProjects(context.getSource())));
        settlement.then(Commands.literal("homes").executes(context -> showHomes(context.getSource())));
        settlement.then(Commands.literal("families").executes(context -> showFamilies(context.getSource())));
        settlement.then(Commands.literal("remove").requires(RealmfolkCommands::canDevelop)
                .then(Commands.literal("confirm").executes(context -> remove(context.getSource()))));

        LiteralArgumentBuilder<CommandSourceStack> brain = Commands.literal("brain")
                .executes(context -> showBrain(context.getSource(), null));
        brain.then(Commands.argument("resident", EntityArgument.entity())
                .executes(context -> showBrain(context.getSource(), EntityArgument.getEntity(context, "resident"))));

        dispatcher.register(Commands.literal("realmfolk")
                .then(settlement)
                .then(Commands.literal("npc").then(brain)));
    }

    private static boolean canDevelop(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        try { return source.getPlayerOrException().getAbilities().instabuild; }
        catch (Exception ignored) { return false; }
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String name = StringArgumentType.getString(context, "name").trim();
        if (name.isBlank() || name.length() > 48) return fail(source, "O nome deve ter entre 1 e 48 caracteres.");
        var bell = SettlementManager.findNearestBell(level, net.minecraft.core.BlockPos.containing(source.getPosition()));
        if (bell.isEmpty()) return fail(source, "Nenhum sino carregado foi encontrado em até 16 blocos.");
        if (SettlementSavedData.get(level.getServer()).at(level.dimension(), bell.get()).isPresent())
            return fail(source, "Este sino já está dentro de outro povoado.");
        Settlement settlement = SettlementManager.create(level, name, bell.get());
        source.sendSuccess(() -> Component.literal("Povoado " + settlement.name() + " criado: "
                + settlement.memberIds().size() + " habitantes, " + settlement.storagePositions().size()
                + " barris e líder " + shortId(settlement.leaderId()) + "."), true);
        return 1;
    }

    private static int showInfo(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        source.sendSuccess(() -> Component.literal(settlement.name() + " [" + settlement.level() + "] | habitantes "
                + settlement.memberIds().size() + " | líder " + shortId(settlement.leaderId()) + " | raio " + settlement.radius()), false);
        return 1;
    }

    private static int showDebug(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        long next = Math.max(0L, 24000L - (source.getLevel().getGameTime() - settlement.lastDailyUpdate()));
        source.sendSuccess(() -> Component.literal("Centro " + settlement.center().toShortString() + " | dimensão "
                + settlement.dimension().location() + " | armazéns " + settlement.storagePositions().size()
                + " | ordens " + settlement.taskBoard().orders().size() + " | próximo ciclo " + next + " ticks"), false);
        return 1;
    }

    private static int showMembers(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        source.sendSuccess(() -> Component.literal("Membros (" + settlement.memberIds().size() + "): "
                + settlement.memberIds().stream().limit(10).map(RealmfolkCommands::shortId).reduce((a, b) -> a + ", " + b).orElse("nenhum")), false);
        return 1;
    }

    private static int showTasks(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        source.sendSuccess(() -> Component.literal("Ordens: " + settlement.taskBoard().orders().stream().limit(8)
                .map(order -> order.type() + "=" + order.status() + (order.blockedReason().isBlank() ? "" : " (" + order.blockedReason() + ")"))
                .reduce((a, b) -> a + " | " + b).orElse("nenhuma")), false);
        return 1;
    }

    private static int showEconomy(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        SettlementStorage storage = new SettlementStorage(source.getLevel(), settlement);
        int treasury = new Treasury(source.getLevel(), settlement).balance();
        source.sendSuccess(() -> Component.literal("Comida " + storage.countFood() + " | tesouro " + treasury
                + " esmeraldas | " + settlement.economy().lastDailySummary()), false);
        return 1;
    }

    private static int showProjects(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        source.sendSuccess(() -> Component.literal("Projetos: " + settlement.projects().stream().limit(6)
                .map(project -> project.blueprintId().getPath() + "=" + project.stage() + " bloco " + project.blockIndex()
                        + (project.blockedReason().isBlank() ? "" : " (" + project.blockedReason() + ")"))
                .reduce((a, b) -> a + " | " + b).orElse("nenhum")), false);
        return 1;
    }

    private static int showHomes(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Voce nao esta dentro de um povoado.");
        boolean changed = HousingManager.refresh(source.getLevel(), settlement);
        changed |= HousingManager.assignHouseholds(settlement, HumanSocietySavedData.get(source.getLevel()));
        if (changed) SettlementSavedData.get(source.getServer()).changed();
        long occupied = settlement.residences().stream().filter(home -> home.householdId() != null).count();
        source.sendSuccess(() -> Component.literal("Residencias validas " + settlement.residences().size()
                + " | ocupadas " + occupied + " | livres " + (settlement.residences().size() - occupied)
                + ". Cada residencia exige cama, piso, paredes e teto carregados."), false);
        return 1;
    }

    private static int showFamilies(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Voce nao esta dentro de um povoado.");
        HumanSocietySavedData society = HumanSocietySavedData.get(source.getLevel());
        long households = society.getHouseholds().stream().filter(household ->
                household.getMemberIds().stream().anyMatch(settlement.memberIds()::contains)).count();
        long pregnancies = settlement.memberIds().stream().map(society::getPerson).flatMap(java.util.Optional::stream)
                .filter(person -> person.isPregnant()).count();
        long children = settlement.memberIds().stream().map(society::getPerson).flatMap(java.util.Optional::stream)
                .filter(person -> person.getLifeStage() == LifeStage.BABY || person.getLifeStage() == LifeStage.CHILD).count();
        source.sendSuccess(() -> Component.literal("Familias " + households + " | gravidezes " + pregnancies
                + " | bebes/criancas " + children + " | residencias " + settlement.residences().size()), false);
        return 1;
    }

    private static int remove(CommandSourceStack source) {
        Settlement settlement = current(source);
        if (settlement == null) return fail(source, "Você não está dentro de um povoado.");
        String name = settlement.name();
        if (!SettlementManager.remove(source.getLevel(), settlement)) return fail(source, "Não foi possível remover o povoado.");
        source.sendSuccess(() -> Component.literal("Povoado " + name + " removido. Famílias e títulos foram preservados."), true);
        return 1;
    }

    private static int showBrain(CommandSourceStack source, Entity selected) {
        ResidentEntity resident = selected instanceof ResidentEntity value ? value : nearestResident(source);
        if (resident == null) return fail(source, "Nenhum habitante encontrado em até 12 blocos.");
        source.sendSuccess(() -> Component.literal(resident.getName().getString() + " | fome " + resident.getNeeds().hunger()
                + " | necessidade " + resident.getNeeds().mostUrgent() + " | ação " + resident.getNpcBrain().currentAction()
                + " | motivo " + resident.getNpcBrain().reason() + " | tarefa " + shortId(resident.getCurrentWorkOrderId())), false);
        return 1;
    }

    private static ResidentEntity nearestResident(CommandSourceStack source) {
        return source.getLevel().getEntitiesOfClass(ResidentEntity.class,
                        new net.minecraft.world.phys.AABB(net.minecraft.core.BlockPos.containing(source.getPosition())).inflate(12.0D), Entity::isAlive)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(source.getPosition()))).orElse(null);
    }

    private static Settlement current(CommandSourceStack source) {
        return SettlementSavedData.get(source.getServer()).at(source.getLevel().dimension(),
                net.minecraft.core.BlockPos.containing(source.getPosition())).orElse(null);
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static String shortId(java.util.UUID id) { return id == null ? "nenhum" : id.toString().substring(0, 8); }
}

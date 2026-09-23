package com.sam.realmfolk.content.item;

import com.sam.realmfolk.client.menu.DocumentMenu;
import com.sam.realmfolk.client.menu.DocumentPage;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.society.HouseholdData;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.construction.Blueprint;
import com.sam.realmfolk.society.construction.BlueprintLoader;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.construction.ConstructionProject;
import com.sam.realmfolk.society.economy.EconomyTransaction;
import com.sam.realmfolk.society.economy.Treasury;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.housing.SettlementResidence;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds the live, server-authoritative pages displayed by Realmfolk documents. */
public final class DocumentContent {
    private DocumentContent() {}

    public static void open(UtilityAction action, ServerLevel level, ServerPlayer player,
                            ItemStack stack, @Nullable Settlement settlement) {
        switch (action) {
            case SETTLEMENT_CHARTER -> DocumentMenu.open(player, "Carta de Fundação", "Leis para criar um povoado",
                    charter(settlement));
            case SETTLEMENT_LEDGER -> DocumentMenu.open(player, "Livro-caixa", settlementName(settlement),
                    ledger(level, settlement));
            case FAMILY_REGISTER -> DocumentMenu.open(player, "Registro das Famílias", settlementName(settlement),
                    families(level, settlement));
            case RESIDENCE_DEED -> DocumentMenu.open(player, "Escritura de Residência", settlementName(settlement),
                    deed(level, stack, settlement));
            case CONSTRUCTION_BLUEPRINT -> DocumentMenu.open(player, "Plantas de Construção", settlementName(settlement),
                    blueprints(stack, settlement));
            case WORK_CONTRACT -> DocumentMenu.open(player, "Contrato de Trabalho", "Profissões e requisitos",
                    professions());
            case WORK_ORDER_BOOK -> DocumentMenu.open(player, "Livro de Ordens", settlementName(settlement),
                    orders(level, settlement));
            case FAMILY_CERTIFICATE -> DocumentMenu.open(player, "Certidão Familiar", "Registro civil",
                    certificate(level, stack));
            default -> { }
        }
    }

    private static List<DocumentPage> charter(@Nullable Settlement settlement) {
        List<DocumentPage> pages = new ArrayList<>();
        pages.add(DocumentPage.of("Como fundar",
                "1. Encontre ou coloque um sino.",
                "2. Deixe barris próximos para servirem como armazém.",
                "3. Reúna moradores sem povoado num raio de 64 blocos.",
                "4. Use esta carta diretamente no sino.",
                "A fundação registra centro, habitantes, líder e depósitos."));
        pages.add(DocumentPage.of("Regras iniciais",
                "O sino define o centro do território.",
                "O primeiro barril válido também funciona como tesouro.",
                "O líder organiza prioridades; ele não cria itens nem constrói sozinho.",
                "Remover o povoado exige confirmação pelo comando administrativo."));
        if (settlement != null) pages.add(DocumentPage.of("Povoado atual",
                "Nome: " + settlement.name(),
                "Nível: " + label(settlement.level().name()),
                "Centro: " + position(settlement.center()),
                "Raio: " + settlement.radius() + " blocos",
                "Habitantes: " + settlement.memberIds().size()));
        return pages;
    }

    private static List<DocumentPage> ledger(ServerLevel level, @Nullable Settlement settlement) {
        if (settlement == null) return outside("Leve o livro para dentro do raio de um povoado.");
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        SettlementStorage storage = new SettlementStorage(level, settlement);
        List<DocumentPage> pages = new ArrayList<>();
        String leader = settlement.leaderId() == null ? "Não escolhido"
                : society.getPerson(settlement.leaderId()).map(PersonData::getDisplayName).orElse("Registro perdido");
        pages.add(DocumentPage.of("Visão geral",
                "Povoado: " + settlement.name(),
                "Nível: " + label(settlement.level().name()),
                "Líder: " + leader,
                "População: " + settlement.memberIds().size(),
                "Casas: " + settlement.residences().size(),
                "Centro: " + position(settlement.center())));
        pages.add(DocumentPage.of("Armazém e tesouro",
                "Comida disponível: " + storage.countFood(),
                "Esmeraldas no tesouro: " + new Treasury(level, settlement).balance(),
                "Barris registrados: " + settlement.storagePositions().size(),
                "Reservas de recursos: " + settlement.storageReservations().size(),
                "Posição do tesouro: " + (settlement.treasuryPosition() == null ? "Não definida" : position(settlement.treasuryPosition()))));
        List<String> people = new ArrayList<>();
        for (UUID personId : settlement.memberIds()) {
            PersonData person = society.getPerson(personId).orElse(null);
            if (person == null) continue;
            Entity entity = person.getEntityId() == null ? null : level.getEntity(person.getEntityId());
            String profession = entity instanceof ResidentEntity resident
                    ? resident.getProfessionData().profession().name : person.getProfession();
            people.add(person.getDisplayName() + " — " + profession + " / " + life(person));
            if (people.size() == 12) break;
        }
        if (people.isEmpty()) people.add("Nenhum habitante registrado.");
        pages.add(new DocumentPage("Habitantes", people));
        List<String> policies = new ArrayList<>();
        for (SettlementPolicy policy : SettlementPolicy.values()) {
            policies.add(label(policy.name()) + ": " + settlement.government().priority(policy) + "/100");
        }
        policies.add("");
        policies.add("Governo: " + (settlement.government().playerControlled() ? "controlado pelo jogador" : "liderança autônoma"));
        pages.add(new DocumentPage("Prioridades", policies));
        pages.add(DocumentPage.of("Último ciclo", settlement.economy().lastDailySummary(),
                "Próxima avaliação ocorre no ciclo diário do povoado."));
        List<String> history = new ArrayList<>();
        for (EconomyTransaction transaction : settlement.economy().history()) {
            history.add(label(transaction.type().name()) + ": " + transaction.amount() + " esmeralda(s) — " + transaction.note());
        }
        if (history.isEmpty()) history.add("Ainda não há transações registradas.");
        else if (history.size() > 10) history = new ArrayList<>(history.subList(history.size() - 10, history.size()));
        pages.add(new DocumentPage("Movimento recente", history));
        return pages;
    }

    private static List<DocumentPage> families(ServerLevel level, @Nullable Settlement settlement) {
        if (settlement == null) return outside("Use o registro dentro do povoado ou diretamente em um morador.");
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        List<DocumentPage> pages = new ArrayList<>();
        int households = 0;
        for (HouseholdData household : society.getHouseholds()) {
            if (household.getMemberIds().stream().noneMatch(settlement.memberIds()::contains)) continue;
            households++;
            List<String> lines = new ArrayList<>();
            for (UUID personId : household.getMemberIds()) {
                PersonData person = society.getPerson(personId).orElse(null);
                if (person != null) lines.add(person.getDisplayName() + " — " + life(person));
            }
            SettlementResidence home = settlement.residenceForHousehold(household.getHouseholdId()).orElse(null);
            lines.add("");
            lines.add(home == null ? "! Sem residência atribuída" : "+ Casa em " + position(home.bedPosition()));
            if (home != null) lines.add("Capacidade: " + household.getMemberIds().size() + "/" + home.capacity());
            pages.add(new DocumentPage("Família " + households, lines));
            if (pages.size() >= 12) break;
        }
        pages.add(0, DocumentPage.of("Censo familiar",
                "Famílias registradas: " + households,
                "Residências reconhecidas: " + settlement.residences().size(),
                "Habitantes no povoado: " + settlement.memberIds().size(),
                "Use este livro em um morador para abrir sua árvore genealógica."));
        return pages;
    }

    private static List<DocumentPage> deed(ServerLevel level, ItemStack stack, @Nullable Settlement settlement) {
        CompoundTag tag = stack.getTag();
        String selected = "Nenhuma família selecionada";
        if (tag != null && tag.hasUUID("HouseholdId")) {
            HouseholdData household = HumanSocietySavedData.get(level).getHousehold(tag.getUUID("HouseholdId")).orElse(null);
            selected = household == null ? "Registro familiar inválido" : familyName(level, household);
        }
        List<DocumentPage> pages = new ArrayList<>();
        pages.add(DocumentPage.of("Procedimento",
                "1. Use a escritura em um membro da família.",
                "2. Entre numa casa válida do mesmo povoado.",
                "3. Use a escritura sobre a cama principal.",
                "4. A residência será vinculada ao núcleo familiar.",
                "Família selecionada: " + selected));
        if (settlement != null) {
            List<String> homes = new ArrayList<>();
            for (SettlementResidence residence : settlement.residences()) {
                String state = residence.householdId() == null ? "Livre" : "Ocupada";
                homes.add(position(residence.bedPosition()) + " — " + state + " — " + residence.capacity() + " vagas");
            }
            if (homes.isEmpty()) homes.add("! Nenhuma casa válida foi reconhecida.");
            pages.add(new DocumentPage("Residências", homes));
        }
        return pages;
    }

    private static List<DocumentPage> blueprints(ItemStack stack, @Nullable Settlement settlement) {
        List<DocumentPage> pages = new ArrayList<>();
        Blueprint blueprint = BlueprintLoader.INSTANCE.get(ConstructionManager.SMALL_STORAGE_HUT).orElse(null);
        if (blueprint == null) {
            pages.add(DocumentPage.of("Pequeno armazém", "! A planta ainda não foi carregada pelo servidor."));
        } else {
            List<String> overview = new ArrayList<>();
            overview.add("Nome: " + blueprint.name());
            overview.add("Dimensões: " + blueprint.size().getX() + " x " + blueprint.size().getY() + " x " + blueprint.size().getZ());
            overview.add("Blocos: " + blueprint.blocks().size());
            overview.add("Nível mínimo: " + label(blueprint.minimumLevel().name()));
            overview.add("Resultado: novo barril de armazenamento coletivo.");
            pages.add(new DocumentPage("Pequeno armazém", overview));
            List<String> materials = new ArrayList<>();
            blueprint.materials().forEach((item, amount) -> materials.add(amount + " x " + itemName(item)));
            pages.add(new DocumentPage("Materiais", materials));
        }
        CompoundTag tag = stack.getTag();
        pages.add(DocumentPage.of("Como construir",
                "1. Use a vara de agrimensor no terreno.",
                "2. Segure esta planta e use-a na área marcada.",
                "3. Deposite os materiais nos barris do povoado.",
                "4. Um construtor executará o projeto aos poucos.",
                tag != null && tag.contains("SurveyPos") ? "+ Área medida: " + position(net.minecraft.core.BlockPos.of(tag.getLong("SurveyPos"))) : "! Nenhuma área medida nesta planta."));
        if (settlement != null) {
            List<String> projects = new ArrayList<>();
            for (ConstructionProject project : settlement.projects()) {
                projects.add(project.blueprintId().getPath() + " — " + label(project.stage().name())
                        + " — bloco " + project.blockIndex());
                if (!project.blockedReason().isBlank()) projects.add("! " + project.blockedReason());
            }
            if (projects.isEmpty()) projects.add("Nenhum projeto ativo.");
            pages.add(new DocumentPage("Projetos do povoado", projects));
        }
        return pages;
    }

    private static List<DocumentPage> professions() {
        List<DocumentPage> pages = new ArrayList<>();
        pages.add(DocumentPage.of("Uso do contrato",
                "Use o contrato diretamente em um morador adulto.",
                "Escolha a profissão na tela aberta.",
                "O morador precisa da estação e da ferramenta indicadas.",
                "A profissão não produz recursos sem materiais reais."));
        List<String> lines = new ArrayList<>();
        int page = 1;
        for (NpcProfession profession : NpcProfession.values()) {
            if (profession == NpcProfession.NONE) continue;
            lines.add(profession.name + " — " + profession.station());
            lines.add("Ferramenta: " + profession.tool());
            if (lines.size() >= 8) {
                pages.add(new DocumentPage("Ofícios " + page++, lines));
                lines = new ArrayList<>();
            }
        }
        if (!lines.isEmpty()) pages.add(new DocumentPage("Ofícios " + page, lines));
        return pages;
    }

    private static List<DocumentPage> orders(ServerLevel level, @Nullable Settlement settlement) {
        if (settlement == null) return outside("Abra o livro dentro do território de um povoado.");
        List<DocumentPage> pages = new ArrayList<>();
        long now = level.getGameTime();
        long active = settlement.taskBoard().orders().stream().filter(order -> !isFinished(order)).count();
        pages.add(DocumentPage.of("Quadro de trabalho",
                "Ordens registradas: " + settlement.taskBoard().orders().size(),
                "Ordens ativas: " + active,
                "Cada ordem só pode ser reservada por um morador.",
                "Ordens bloqueadas mostram a causa para evitar tentativas infinitas."));
        int number = 1;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            List<String> lines = new ArrayList<>();
            lines.add("Tipo: " + label(order.type().name()));
            lines.add("Estado: " + label(order.status().name()));
            lines.add("Prioridade: " + order.priority() + "/100");
            lines.add("Destino: " + (order.targetPosition() == null ? "Centro do povoado" : position(order.targetPosition())));
            if (order.requirement() != net.minecraft.world.item.Items.AIR) lines.add("Requer: " + order.requiredAmount() + " x " + itemName(order.requirement()));
            if (order.result() != net.minecraft.world.item.Items.AIR) lines.add("Produz: " + order.resultAmount() + " x " + itemName(order.result()));
            lines.add("Responsável: " + assigned(level, order));
            if (!order.blockedReason().isBlank()) lines.add("! Bloqueio: " + order.blockedReason());
            if (order.expiresAt() > 0) lines.add("Expira em: " + Math.max(0, (order.expiresAt() - now) / 20) + "s");
            pages.add(new DocumentPage("Ordem " + number++, lines));
            if (pages.size() >= 16) break;
        }
        return pages;
    }

    private static List<DocumentPage> certificate(ServerLevel level, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID("PersonId")) return List.of(DocumentPage.of("Certidão vazia",
                "Use esta certidão diretamente em um morador para registrar sua identidade e abrir os dados familiares."));
        PersonData person = HumanSocietySavedData.get(level).getPerson(tag.getUUID("PersonId")).orElse(null);
        if (person == null) return List.of(DocumentPage.of("Registro inválido", "! O titular não existe mais nos registros."));
        return List.of(DocumentPage.of("Dados do titular",
                "Nome: " + person.getDisplayName(),
                "Idade: " + person.getAge() + " anos",
                "Fase: " + life(person),
                "Gênero: " + (person.getGender().name().equals("MALE") ? "Masculino" : "Feminino"),
                "Filhos registrados: " + person.getChildrenIds().size(),
                "Estado: " + label(person.getStatus().name())));
    }

    private static List<DocumentPage> outside(String instruction) {
        return List.of(DocumentPage.of("Sem povoado", "! Nenhum povoado foi encontrado nesta posição.", instruction));
    }

    private static String assigned(ServerLevel level, WorkOrder order) {
        if (order.assignedNpcId() == null) return "Ninguém";
        return HumanSocietySavedData.get(level).getPerson(order.assignedNpcId())
                .map(PersonData::getDisplayName).orElse("Morador desconhecido");
    }

    private static boolean isFinished(WorkOrder order) {
        return switch (order.status()) {
            case COMPLETED, CANCELLED -> true;
            default -> false;
        };
    }

    private static String familyName(ServerLevel level, HouseholdData household) {
        for (UUID id : household.getMemberIds()) {
            PersonData person = HumanSocietySavedData.get(level).getPerson(id).orElse(null);
            if (person != null) return "Família de " + person.getDisplayName();
        }
        return "Família sem membros";
    }

    private static String life(PersonData person) {
        return switch (person.getLifeStage()) {
            case BABY -> "bebê";
            case CHILD -> "criança";
            case TEENAGER -> "adolescente";
            case ADULT -> "adulto";
            case ELDER -> "idoso";
        };
    }

    private static String settlementName(@Nullable Settlement settlement) {
        return settlement == null ? "Fora de um povoado" : settlement.name();
    }

    private static String position(net.minecraft.core.BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static String itemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private static String label(String value) {
        String spaced = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}

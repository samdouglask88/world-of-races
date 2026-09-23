package com.sam.realmfolk.client.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

/** Read-only network menu used by ledgers, registers, contracts and plans. */
public final class DocumentMenu extends AbstractContainerMenu {
    private static final int MAX_PAGES = 24;
    private final String documentTitle;
    private final String subtitle;
    private final List<DocumentPage> pages;

    public DocumentMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenuTypes.DOCUMENT_MENU.get(), id);
        documentTitle = buffer.readUtf(80);
        subtitle = buffer.readUtf(120);
        int size = Math.min(MAX_PAGES, Math.max(1, buffer.readVarInt()));
        List<DocumentPage> received = new ArrayList<>(size);
        for (int i = 0; i < size; i++) received.add(DocumentPage.read(buffer));
        pages = List.copyOf(received);
    }

    private DocumentMenu(int id, Inventory inventory, String title, String subtitle, List<DocumentPage> pages) {
        super(ModMenuTypes.DOCUMENT_MENU.get(), id);
        this.documentTitle = title;
        this.subtitle = subtitle;
        this.pages = safePages(pages);
    }

    public String documentTitle() { return documentTitle; }
    public String subtitle() { return subtitle; }
    public List<DocumentPage> pages() { return pages; }

    public static void open(ServerPlayer player, String title, String subtitle, List<DocumentPage> requestedPages) {
        String safeTitle = limited(title, 80);
        String safeSubtitle = limited(subtitle, 120);
        List<DocumentPage> pages = safePages(requestedPages);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, ignored) -> new DocumentMenu(id, inventory, safeTitle, safeSubtitle, pages),
                Component.literal(safeTitle)), buffer -> {
            buffer.writeUtf(safeTitle, 80);
            buffer.writeUtf(safeSubtitle, 120);
            buffer.writeVarInt(pages.size());
            pages.forEach(page -> page.write(buffer));
        });
    }

    private static List<DocumentPage> safePages(List<DocumentPage> requested) {
        if (requested == null || requested.isEmpty()) return List.of(DocumentPage.of("Sem registros", "Nenhuma informação disponível."));
        return List.copyOf(requested.stream().limit(MAX_PAGES).toList());
    }

    private static String limited(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length);
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}

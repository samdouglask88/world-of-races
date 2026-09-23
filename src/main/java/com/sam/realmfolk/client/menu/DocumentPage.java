package com.sam.realmfolk.client.menu;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** One server-authored page shown in a Realmfolk document. */
public record DocumentPage(String heading, List<String> lines) {
    public static final int MAX_LINES = 18;

    public DocumentPage {
        heading = clean(heading, 80);
        lines = List.copyOf(lines.stream().limit(MAX_LINES).map(line -> clean(line, 180)).toList());
    }

    public static DocumentPage of(String heading, String... lines) {
        return new DocumentPage(heading, List.of(lines));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(heading, 80);
        buffer.writeVarInt(lines.size());
        for (String line : lines) buffer.writeUtf(line, 180);
    }

    public static DocumentPage read(FriendlyByteBuf buffer) {
        String heading = buffer.readUtf(80);
        int size = Math.min(MAX_LINES, Math.max(0, buffer.readVarInt()));
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) lines.add(buffer.readUtf(180));
        return new DocumentPage(heading, lines);
    }

    private static String clean(String value, int maximum) {
        if (value == null) return "";
        String result = value.strip();
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }
}

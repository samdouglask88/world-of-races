package com.sam.realmfolk.society.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SettlementEconomy {
    public static final int MAX_HISTORY = 64;
    private final ArrayDeque<EconomyTransaction> history = new ArrayDeque<>();
    private final Map<UUID, Integer> incomeSinceLastCycle = new LinkedHashMap<>();
    private String lastDailySummary = "Ainda não houve ciclo econômico.";

    public Iterable<EconomyTransaction> history() { return Collections.unmodifiableCollection(history); }
    public Map<UUID, Integer> incomeSinceLastCycle() { return Collections.unmodifiableMap(incomeSinceLastCycle); }
    public String lastDailySummary() { return lastDailySummary; }

    public void record(EconomyTransaction transaction) {
        history.addLast(transaction);
        while (history.size() > MAX_HISTORY) history.removeFirst();
    }

    public void recordIncome(UUID personId, int amount) {
        if (amount > 0) incomeSinceLastCycle.merge(personId, amount, Integer::sum);
    }

    public Map<UUID, Integer> consumeIncomeLedger() {
        Map<UUID, Integer> snapshot = new LinkedHashMap<>(incomeSinceLastCycle);
        incomeSinceLastCycle.clear();
        return snapshot;
    }

    public void setLastDailySummary(String summary) { lastDailySummary = summary; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag historyTag = new ListTag();
        history.forEach(transaction -> historyTag.add(transaction.save()));
        tag.put("History", historyTag);
        ListTag incomeTag = new ListTag();
        incomeSinceLastCycle.forEach((personId, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("PersonId", personId);
            entry.putInt("Amount", amount);
            incomeTag.add(entry);
        });
        tag.put("Income", incomeTag);
        tag.putString("LastDailySummary", lastDailySummary);
        return tag;
    }

    public static SettlementEconomy load(CompoundTag tag) {
        SettlementEconomy economy = new SettlementEconomy();
        ListTag historyTag = tag.getList("History", Tag.TAG_COMPOUND);
        int start = Math.max(0, historyTag.size() - MAX_HISTORY);
        for (int i = start; i < historyTag.size(); i++) economy.history.addLast(EconomyTransaction.load(historyTag.getCompound(i)));
        ListTag incomeTag = tag.getList("Income", Tag.TAG_COMPOUND);
        for (int i = 0; i < incomeTag.size(); i++) {
            CompoundTag entry = incomeTag.getCompound(i);
            if (entry.hasUUID("PersonId") && entry.getInt("Amount") > 0) economy.incomeSinceLastCycle.put(entry.getUUID("PersonId"), entry.getInt("Amount"));
        }
        if (tag.contains("LastDailySummary")) economy.lastDailySummary = tag.getString("LastDailySummary");
        return economy;
    }
}

package com.sam.realmfolk.society.task;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TaskBoard {
    public static final int MAX_ORDERS = 64;
    private final List<WorkOrder> orders = new ArrayList<>();

    public List<WorkOrder> orders() { return Collections.unmodifiableList(orders); }
    public Optional<WorkOrder> get(UUID id) { return orders.stream().filter(order -> order.id().equals(id)).findFirst(); }

    public boolean add(WorkOrder order) {
        if (orders.size() >= MAX_ORDERS || get(order.id()).isPresent()) return false;
        orders.add(order);
        return true;
    }

    public Optional<WorkOrder> bestAvailable(java.util.function.Predicate<WorkOrder> filter) {
        return orders.stream().filter(order -> order.status() == WorkOrderStatus.AVAILABLE)
                .filter(filter).max(Comparator.comparingInt(WorkOrder::priority)
                        .thenComparing(WorkOrder::createdGameTime, Comparator.reverseOrder()));
    }

    public boolean maintain(long now) {
        boolean changed = false;
        Iterator<WorkOrder> iterator = orders.iterator();
        while (iterator.hasNext()) {
            WorkOrder order = iterator.next();
            if (order.reservationExpired(now)) { order.release(); changed = true; }
            if (order.isExpired(now) && order.status() != WorkOrderStatus.COMPLETED) { order.cancel(); changed = true; }
            if ((order.status() == WorkOrderStatus.COMPLETED || order.status() == WorkOrderStatus.CANCELLED)
                    && now - order.createdGameTime() > 24000L) { iterator.remove(); changed = true; }
        }
        return changed;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        orders.forEach(order -> list.add(order.save()));
        tag.put("Orders", list);
        return tag;
    }

    public static TaskBoard load(CompoundTag tag) {
        TaskBoard board = new TaskBoard();
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && board.orders.size() < MAX_ORDERS; i++) board.orders.add(WorkOrder.load(list.getCompound(i)));
        return board;
    }
}

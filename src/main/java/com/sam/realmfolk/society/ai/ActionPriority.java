package com.sam.realmfolk.society.ai;

public final class ActionPriority {
    public static final int IMMEDIATE_DANGER = 100;
    public static final int CRITICAL_HUNGER = 90;
    public static final int DIRECT_PLAYER_ORDER = 75;
    public static final int BASIC_NEED = 70;
    public static final int WORK = 60;
    public static final int DEPOSIT = 50;
    public static final int SOCIAL = 20;
    public static final int IDLE = 5;

    private ActionPriority() {}
}

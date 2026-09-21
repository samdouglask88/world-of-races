package com.example.worldofraces.profession;

public enum ProfessionCategory {
    PRODUCTION("Produção"), GATHERING("Coleta"), SERVICES("Serviços"), MILITARY("Militar");

    public final String displayName;

    ProfessionCategory(String displayName) {
        this.displayName = displayName;
    }
}

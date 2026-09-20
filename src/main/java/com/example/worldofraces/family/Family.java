package com.example.worldofraces.family;

import java.util.UUID;

public class Family {

    private final UUID id;
    private final String surname;

    public Family(String surname) {
        this.id = UUID.randomUUID();
        this.surname = surname;
    }

    public UUID getId() {
        return id;
    }

    public String getSurname() {
        return surname;
    }
}
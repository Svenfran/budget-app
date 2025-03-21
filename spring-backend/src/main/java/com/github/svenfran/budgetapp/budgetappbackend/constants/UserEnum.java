package com.github.svenfran.budgetapp.budgetappbackend.constants;

public enum UserEnum {

    USER_DELETED("Nutzer gelöscht"),
    USER_REMOVED("Nutzer entfernt");

    private final String name;

    UserEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}

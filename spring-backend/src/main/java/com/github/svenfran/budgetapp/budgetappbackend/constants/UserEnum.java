package com.github.svenfran.budgetapp.budgetappbackend.constants;

public enum UserEnum {

    USER_DELETED("DELETED"),
    USER_REMOVED("REMOVED");

    private final String name;

    UserEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}

package com.github.svenfran.budgetapp.budgetappbackend.constants;

public enum UserEnum {

    USER_DELETED("USER_DELETED"),
    USER_REMOVED("USER_REMOVED");

    private final String name;

    UserEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}

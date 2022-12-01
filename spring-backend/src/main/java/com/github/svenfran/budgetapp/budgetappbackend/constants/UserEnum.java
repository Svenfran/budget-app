package com.github.svenfran.budgetapp.budgetappbackend.constants;

public enum UserEnum {
    CURRENT_USER(1L),

    SVEN(1L),
    SASCHA(2L),
    BASTI(3L),
    MARTIN(4L),
    SABINE(5L),
    TINA(6L),
    MONTSE(7L);

    private final Long id;

    UserEnum(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

}

package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

public class GroupSideNavDto {

    private Long id;
    private String name;

    public GroupSideNavDto(Group group) {
        this.id = group.getId();
        this.name = group.getName();
    }

    public GroupSideNavDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

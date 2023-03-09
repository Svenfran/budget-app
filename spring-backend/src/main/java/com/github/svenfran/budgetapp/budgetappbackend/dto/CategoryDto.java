package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;

import javax.validation.constraints.NotNull;

public class CategoryDto {

    private Long id;
    private String name;
    @NotNull
    private Long groupId;

    public CategoryDto(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.groupId = category.getGroup().getId();
    }

    public CategoryDto() {
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}

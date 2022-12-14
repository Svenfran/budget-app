package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

import java.util.Date;


public class GroupSideNavDto {

    private Long id;
    private String name;
    private Date dateCreated;


    public GroupSideNavDto(Group group) {
        this.id = group.getId();
        this.name = group.getName();
        this.dateCreated = group.getDateCreated();
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

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }
}

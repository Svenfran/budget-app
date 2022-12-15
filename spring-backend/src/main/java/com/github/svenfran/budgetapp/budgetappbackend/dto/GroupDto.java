package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

import java.util.Date;

public class GroupDto extends GroupSideNavDto{

    private Date dateCreated;

    public GroupDto(Group group) {
        super(group);
        this.dateCreated = group.getDateCreated();
    }

    public GroupDto() {
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }
}

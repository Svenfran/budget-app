package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

import java.util.Date;

public class GroupDto extends GroupSideNavDto{

//    private UserDto owner;
    private Date dateCreated;

    public GroupDto(Group group) {
        super(group);
        this.dateCreated = group.getDateCreated();
//        this.owner = new UserDto(group.getOwner());
    }

    public GroupDto() {
    }

//    public UserDto getOwner() {
//        return owner;
//    }
//
//    public void setOwner(UserDto owner) {
//        this.owner = owner;
//    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }
}

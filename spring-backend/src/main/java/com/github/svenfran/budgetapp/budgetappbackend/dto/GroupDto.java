package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

public class GroupDto extends GroupSideNavDto{

    private UserDto owner;

    public GroupDto(Group group) {
        super(group);
        this.owner = new UserDto(group.getOwner());
    }

    public GroupDto() {
    }

    public UserDto getOwner() {
        return owner;
    }

    public void setOwner(UserDto owner) {
        this.owner = owner;
    }
}

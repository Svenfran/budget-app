package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

import java.util.List;

public class GroupMembersDto extends GroupSideNavDto{

    private String ownerName;
    private Long ownerId;
    private List<UserDto> members;

    public GroupMembersDto(Group group) {
        super(group);
        this.ownerName = group.getOwner().getName();
        this.ownerId = group.getOwner().getId();
        this.members = group.getMembers().stream().map(UserDto::new).toList();
    }

    public GroupMembersDto() {

    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<UserDto> getMembers() {
        return members;
    }

    public void setMembers(List<UserDto> members) {
        this.members = members;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

}

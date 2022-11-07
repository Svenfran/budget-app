package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class ChangeGroupOwnerDto {
    private UserDto newOwner;
    private Long groupId;

    public ChangeGroupOwnerDto(UserDto newOwner, Long groupId) {
        this.newOwner = newOwner;
        this.groupId = groupId;
    }

    public ChangeGroupOwnerDto() {
    }

    public UserDto getNewOwner() {
        return newOwner;
    }

    public void setNewOwner(UserDto newOwner) {
        this.newOwner = newOwner;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}

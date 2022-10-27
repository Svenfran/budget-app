package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

public class AddGroupMemberDto extends GroupSideNavDto {

    private String newMemberEmail;

    public AddGroupMemberDto(Group group, String newMemberEmail) {
        super(group);
        this.newMemberEmail = newMemberEmail;
    }

    public AddGroupMemberDto() {
    }

    public String getNewMemberEmail() {
        return newMemberEmail;
    }

    public void setNewMemberEmail(String newMemberEmail) {
        this.newMemberEmail = newMemberEmail;
    }
}

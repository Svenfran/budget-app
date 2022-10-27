package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

public class RemoveGroupMemberDto extends GroupSideNavDto {

    public UserDto member;

    public RemoveGroupMemberDto(Group group, UserDto member) {
        super(group);
        this.member = member;
    }

    public RemoveGroupMemberDto() {}


    public UserDto getMember() {
        return member;
    }

    public void setMember(UserDto member) {
        this.member = member;
    }
}

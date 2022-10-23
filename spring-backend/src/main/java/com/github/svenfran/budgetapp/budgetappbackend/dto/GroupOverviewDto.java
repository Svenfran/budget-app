package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

public class GroupOverviewDto extends GroupSideNavDto {

    private String ownerName;
    private int memberCount;

    public GroupOverviewDto(Group group) {
        super(group);
        this.ownerName = group.getOwner().getUserName();
        this.memberCount = group.getMembers().size();
    }

    public GroupOverviewDto() {
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}

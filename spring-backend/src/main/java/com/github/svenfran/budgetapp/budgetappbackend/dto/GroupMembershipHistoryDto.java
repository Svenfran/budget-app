package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;

import java.util.Date;

public class GroupMembershipHistoryDto {

    private Date startDate;
    private Date endDate;
    private Long groupId;
    private Long userId;
    private String userName;

    public GroupMembershipHistoryDto(GroupMembershipHistory gmh) {
        this.startDate = gmh.getMembershipStart();
        this.endDate = gmh.getMembershipEnd();
        this.groupId = gmh.getGroupId();
        this.userId = gmh.getUserId();
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

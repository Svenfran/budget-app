package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

public class GroupOverviewDto extends GroupSideNavDto {

    private String ownerName;
    /** User-ID des Owners: das Frontend erkennt den Gruppenadmin daran statt am Namen (DIV-80). */
    private Long ownerId;
    private int memberCount;
    /** Hat die Gruppe Buchungen (Carts)? Steuert den Bestätigungsdialog beim Währungswechsel (DIV-77). */
    private boolean hasBookings;

    public GroupOverviewDto(Group group) {
        super(group);
        this.ownerName = group.getOwner().getName();
        this.ownerId = group.getOwner().getId();
        this.memberCount = group.getMembers().size();
    }

    public GroupOverviewDto(Group group, boolean hasBookings) {
        this(group);
        this.hasBookings = hasBookings;
    }

    public GroupOverviewDto() {
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public boolean isHasBookings() {
        return hasBookings;
    }

    public void setHasBookings(boolean hasBookings) {
        this.hasBookings = hasBookings;
    }


}

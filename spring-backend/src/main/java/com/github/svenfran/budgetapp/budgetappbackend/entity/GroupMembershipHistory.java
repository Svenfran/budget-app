package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;


@Entity
@Table(name = "group_membership_history")
public class GroupMembershipHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "membership_start")
    private Date membershipStart;

    @Column(name = "membership_end")
    private Date membershipEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TypeEnum type;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    public GroupMembershipHistory(Long id, Date membershipStart, Date membershipEnd, TypeEnum type, Long userId, Long groupId) {
        this.id = id;
        this.membershipStart = membershipStart;
        this.membershipEnd = membershipEnd;
        this.type = type;
        this.userId = userId;
        this.groupId = groupId;
    }

    public GroupMembershipHistory() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getMembershipStart() {
        return membershipStart;
    }

    public void setMembershipStart(Date membershipStart) {
        this.membershipStart = membershipStart;
    }

    public Date getMembershipEnd() {
        return membershipEnd;
    }

    public void setMembershipEnd(Date membershipEnd) {
        this.membershipEnd = membershipEnd;
    }

    public TypeEnum getType() {
        return type;
    }

    public void setType(TypeEnum type) {
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}

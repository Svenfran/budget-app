package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Protokolleintrag eines Währungswechsels einer Gruppe (DIV-77): alte/neue Währung, Zeitpunkt, auslösender Nutzer.
 * Wird in derselben Transaktion wie der Wechsel geschrieben; keine UI, kein Export. Gruppe und Nutzer nur als IDs
 * (Muster {@link ShoppingPurchase}); die Einträge löscht die Datenbank mit der Gruppe ({@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "group_currency_change")
public class GroupCurrencyChange implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_currency_code", nullable = false, length = 3)
    private SupportedCurrency oldCurrencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_currency_code", nullable = false, length = 3)
    private SupportedCurrency newCurrencyCode;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Date changedAt;

    public GroupCurrencyChange(Long groupId, SupportedCurrency oldCurrencyCode, SupportedCurrency newCurrencyCode,
                               Long changedByUserId, Date changedAt) {
        this.groupId = groupId;
        this.oldCurrencyCode = oldCurrencyCode;
        this.newCurrencyCode = newCurrencyCode;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
    }

    public GroupCurrencyChange() {
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public SupportedCurrency getOldCurrencyCode() {
        return oldCurrencyCode;
    }

    public SupportedCurrency getNewCurrencyCode() {
        return newCurrencyCode;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public Date getChangedAt() {
        return changedAt;
    }

    @Override
    public String toString() {
        return "GroupCurrencyChange{" +
                "id=" + id +
                ", groupId=" + groupId +
                ", oldCurrencyCode=" + oldCurrencyCode +
                ", newCurrencyCode=" + newCurrencyCode +
                ", changedByUserId=" + changedByUserId +
                ", changedAt=" + changedAt +
                '}';
    }
}

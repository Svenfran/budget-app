package com.github.svenfran.budgetapp.budgetappbackend.entity;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Ausgleichszahlung (DIV-74): einzige Wahrheit für Zahler, Empfänger, Betrag (immer > 0) und Datum. Die beiden Carts
 * (Zahler +amount, Empfänger -amount) sind daraus abgeleitete Buchungen und verweisen über {@code cart.settlement_id}
 * hierher; SettlementService setzt sie bei jeder Änderung vollständig neu. {@code createdBy} ist bei migrierten
 * Altpaaren {@code null} (Ersteller unbekannt).
 */
@Entity
@Table(name = "settlement")
public class Settlement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "date_purchased", nullable = false)
    private Date datePurchased;

    @Column(name = "date_created", updatable = false)
    @CreationTimestamp
    private Date dateCreated;

    @Column(name = "last_update")
    @UpdateTimestamp
    private Date lastUpdate;

    public Settlement() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public User getPayer() {
        return payer;
    }

    public void setPayer(User payer) {
        this.payer = payer;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Date getDatePurchased() {
        return datePurchased;
    }

    public void setDatePurchased(Date datePurchased) {
        this.datePurchased = datePurchased;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public String toString() {
        return "Settlement{" +
                "id=" + id +
                ", amount=" + amount +
                ", datePurchased=" + datePurchased +
                ", dateCreated=" + dateCreated +
                ", lastUpdate=" + lastUpdate +
                ", groupId=" + (group == null ? null : group.getId()) +
                ", payerId=" + (payer == null ? null : payer.getId()) +
                ", receiverId=" + (receiver == null ? null : receiver.getId()) +
                ", createdById=" + (createdBy == null ? null : createdBy.getId()) +
                '}';
    }
}

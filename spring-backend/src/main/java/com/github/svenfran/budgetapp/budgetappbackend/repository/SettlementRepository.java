package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Settlement;
import org.springframework.data.repository.CrudRepository;

public interface SettlementRepository extends CrudRepository<Settlement, Long> {

    /**
     * Löscht alle Settlements einer Gruppe (Gruppe löschen). Abgeleitete Löschmethode: lädt und entfernt die Entities
     * einzeln, damit Hibernate die Deletes in Aufrufreihenfolge ausführt – die Carts mit {@code settlement_id} müssen
     * vorher gelöscht sein ({@code fk_cart_settlement}). Eine JPQL-Massenlöschung würde vor den noch ausstehenden
     * Cart-Deletes laufen.
     */
    void deleteByGroupId(Long groupId);
}

package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Settlement;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface SettlementRepository extends CrudRepository<Settlement, Long> {

    /**
     * Lädt das Settlement mit Zeilensperre (SELECT … FOR UPDATE): Änderungen und Löschen desselben Settlements laufen
     * nacheinander, das Paar ist danach immer konsistent (DIV-74, Last-Write-Wins auf das ganze Paar). Muss der erste
     * Zugriff auf das Settlement in der Transaktion sein.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id")
    Optional<Settlement> findByIdForUpdate(@Param("id") Long id);

    /**
     * Löscht alle Settlements einer Gruppe (Gruppe löschen). Abgeleitete Löschmethode: lädt und entfernt die Entities
     * einzeln, damit Hibernate die Deletes in Aufrufreihenfolge ausführt – die Carts mit {@code settlement_id} müssen
     * vorher gelöscht sein ({@code fk_cart_settlement}). Eine JPQL-Massenlöschung würde vor den noch ausstehenden
     * Cart-Deletes laufen.
     */
    void deleteByGroupId(Long groupId);
}

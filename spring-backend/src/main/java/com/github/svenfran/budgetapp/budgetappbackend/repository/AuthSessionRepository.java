package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.AuthSession;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sessions (DIV-65). Sperrreihenfolge überall: erst die Nutzerzeile ({@code UserRepository.findByIdForUpdate}), dann
 * Session-Zeilen. Der Refresh sperrt nur die Session.
 */
public interface AuthSessionRepository extends CrudRepository<AuthSession, Long> {

    /**
     * Lädt die Session mit Zeilensperre (SELECT … FOR UPDATE): Rotation, Widerruf und Logout derselben Session laufen
     * nacheinander. Muss der erste Zugriff auf die Session in der Transaktion sein.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") Long id);

    /** Sperrt die nicht widerrufenen Sessions des Nutzers auf dem Gerät (nach Id sortiert). Nur unter Nutzersperre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.user.id = :userId and s.deviceId = :deviceId and s.revokedAt is null order by s.id")
    List<AuthSession> lockNotRevokedByUserIdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    /** Sperrt alle nicht widerrufenen Sessions des Nutzers (nach Id sortiert). Nur unter Nutzersperre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.user.id = :userId and s.revokedAt is null order by s.id")
    List<AuthSession> lockNotRevokedByUserId(@Param("userId") Long userId);

    /**
     * Löscht alle Sessions des Nutzers; ihre Refresh Tokens entfernt die Datenbank ({@code ON DELETE CASCADE}).
     * Bereits geladene Session-/Token-Entities im Persistence Context sind danach veraltet.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from AuthSession s where s.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Löscht beendete Sessions (inkl. Refresh Tokens per Cascade): widerrufen vor {@code threshold}, absolute Grenze vor
     * {@code threshold} oder kein aktueller Refresh Token mehr, der bis {@code threshold} gültig war.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from AuthSession s
            where s.revokedAt < :threshold
               or s.absoluteExpiresAt < :threshold
               or not exists (select r.id from RefreshToken r
                              where r.session = s and r.rotatedAt is null and r.expiresAt >= :threshold)
            """)
    int deleteEndedBefore(@Param("threshold") LocalDateTime threshold);
}

package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.RefreshToken;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    /** Nur die Ids eines Refresh Tokens – lädt keine Entity in den Persistence Context. */
    interface RefreshTokenRef {
        Long getTokenId();

        Long getSessionId();
    }

    /**
     * Ids zu einem Token-Hash als skalare Projektion. Wird vor der Session-Sperre aufgerufen; die Entities werden erst
     * nach der Sperre geladen, damit sie den Stand nach einem konkurrierenden Commit zeigen.
     */
    @Query("select t.id as tokenId, t.session.id as sessionId from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshTokenRef> findRefByTokenHash(@Param("tokenHash") String tokenHash);

    /** Alle Tokens einer Session, nach Id (Ausstellungsreihenfolge). */
    List<RefreshToken> findBySessionIdOrderById(Long sessionId);
}

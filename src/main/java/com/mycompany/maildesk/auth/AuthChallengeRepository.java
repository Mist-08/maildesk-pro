package com.mycompany.maildesk.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, Long> {

    Optional<AuthChallenge> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, ChallengePurpose purpose);

    /**
     * Incremento atómico de intentos: solo tiene efecto si el desafío sigue vivo. Devuelve 1 si el
     * intento fue admitido y 0 si el desafío está consumido, invalidado, vencido o agotado.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthChallenge c set c.attempts = c.attempts + 1 where c.id = :id and c.consumedAt is null "
            + "and c.invalidatedAt is null and c.attempts < c.maxAttempts and c.expiresAt > :now")
    int registerAttempt(@Param("id") Long id, @Param("now") Instant now);

    /** Consumo atómico: exactamente una verificación concurrente puede ganar. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthChallenge c set c.consumedAt = :now where c.id = :id and c.consumedAt is null "
            + "and c.invalidatedAt is null")
    int consume(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthChallenge c set c.invalidatedAt = :now where c.userId = :userId and c.purpose = :purpose "
            + "and c.consumedAt is null and c.invalidatedAt is null")
    int invalidateActive(@Param("userId") Long userId, @Param("purpose") ChallengePurpose purpose,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthChallenge c set c.invalidatedAt = :now where c.id = :id and c.invalidatedAt is null")
    int invalidate(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthChallenge c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}

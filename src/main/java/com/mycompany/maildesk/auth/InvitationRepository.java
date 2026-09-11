package com.mycompany.maildesk.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    /** Marca la invitación como usada de forma atómica (un solo uso). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Invitation i set i.usedAt = :now where i.id = :id and i.usedAt is null and i.expiresAt > :now")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Invitation i set i.usedAt = :now where i.email = :email and i.usedAt is null")
    int revokePendingFor(@Param("email") String email, @Param("now") Instant now);
}

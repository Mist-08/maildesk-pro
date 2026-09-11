package com.mycompany.maildesk.messages;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("select m from Message m where m.ownerId = :ownerId and m.status in :statuses "
            + "and (lower(coalesce(m.subject, '')) like :q or lower(coalesce(m.toRecipients, '')) like :q) "
            + "order by m.updatedAt desc")
    Page<Message> search(@Param("ownerId") Long ownerId, @Param("statuses") Collection<MessageStatus> statuses,
                         @Param("q") String q, Pageable pageable);

    long countByOwnerIdAndStatus(Long ownerId, MessageStatus status);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<MessageStatus> statuses);

    long countByOwnerIdAndStatusAndSentAtAfter(Long ownerId, MessageStatus status, Instant after);

    long countByOwnerIdAndStatusInAndUpdatedAtAfter(Long ownerId, Collection<MessageStatus> statuses, Instant after);

    long countByOwnerIdAndQueuedAtAfter(Long ownerId, Instant after);

    long countByStatusIn(Collection<MessageStatus> statuses);

    List<Message> findTop5ByOwnerIdAndStatusInOrderByUpdatedAtDesc(Long ownerId, Collection<MessageStatus> statuses);

    @Query("select m.id from Message m where m.status = com.mycompany.maildesk.messages.MessageStatus.QUEUED "
            + "and (m.nextAttemptAt is null or m.nextAttemptAt <= :now) order by m.queuedAt asc")
    List<Long> findDueQueuedIds(@Param("now") Instant now, Pageable pageable);

    /** Reclamación atómica para un trabajador: solo uno puede pasar de QUEUED a SENDING. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.status = com.mycompany.maildesk.messages.MessageStatus.SENDING, m.lockedAt = :now, "
            + "m.updatedAt = :now where m.id = :id and m.status = com.mycompany.maildesk.messages.MessageStatus.QUEUED")
    int claim(@Param("id") Long id, @Param("now") Instant now);

    /** Puesta en cola atómica desde borrador (protege contra doble clic). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.status = com.mycompany.maildesk.messages.MessageStatus.QUEUED, m.queuedAt = :now, "
            + "m.nextAttemptAt = :now, m.submissionToken = :token, m.attempts = 0, m.lastError = null, m.updatedAt = :now "
            + "where m.id = :id and m.ownerId = :ownerId and m.status = com.mycompany.maildesk.messages.MessageStatus.DRAFT")
    int enqueueDraft(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("token") String token,
                     @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.status = com.mycompany.maildesk.messages.MessageStatus.CANCELLED, m.updatedAt = :now "
            + "where m.id = :id and m.ownerId = :ownerId and m.status = com.mycompany.maildesk.messages.MessageStatus.QUEUED")
    int cancelQueued(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.status = com.mycompany.maildesk.messages.MessageStatus.QUEUED, m.queuedAt = :now, "
            + "m.nextAttemptAt = :now, m.attempts = 0, m.lastError = null, m.failedAt = null, m.updatedAt = :now "
            + "where m.id = :id and m.ownerId = :ownerId and m.status = com.mycompany.maildesk.messages.MessageStatus.FAILED")
    int requeueFailed(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("now") Instant now);

    /** Mensajes atascados en SENDING (p. ej. reinicio a mitad de envío): su resultado es incierto. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.status = com.mycompany.maildesk.messages.MessageStatus.UNCERTAIN, "
            + "m.lastError = 'Proceso interrumpido durante el envío; resultado desconocido', m.updatedAt = :now "
            + "where m.status = com.mycompany.maildesk.messages.MessageStatus.SENDING and m.lockedAt < :before")
    int markStaleSendingAsUncertain(@Param("before") Instant before, @Param("now") Instant now);
}

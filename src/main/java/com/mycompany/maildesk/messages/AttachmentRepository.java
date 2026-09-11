package com.mycompany.maildesk.messages;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByMessageIdOrderByCreatedAtAsc(Long messageId);

    Optional<Attachment> findByIdAndOwnerId(Long id, Long ownerId);

    long countByMessageId(Long messageId);
}

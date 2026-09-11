package com.mycompany.maildesk.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {
    Page<AuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditEntry> findByActionContainingIgnoreCaseOrActorEmailContainingIgnoreCaseOrderByCreatedAtDesc(
            String action, String actorEmail, Pageable pageable);
}

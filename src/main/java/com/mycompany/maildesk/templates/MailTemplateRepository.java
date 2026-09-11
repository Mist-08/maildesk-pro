package com.mycompany.maildesk.templates;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    Optional<MailTemplate> findByIdAndOwnerId(Long id, Long ownerId);

    Page<MailTemplate> findByOwnerIdAndNameContainingIgnoreCaseOrderByNameAsc(Long ownerId, String q, Pageable pageable);

    List<MailTemplate> findByOwnerIdOrderByNameAsc(Long ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

    long countByOwnerId(Long ownerId);
}

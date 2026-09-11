package com.mycompany.maildesk.contacts;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndEmailIgnoreCase(Long ownerId, String email);

    long countByOwnerId(Long ownerId);

    @Query("select c from Contact c where c.ownerId = :ownerId and (lower(c.fullName) like :q or lower(c.email) like :q "
            + "or lower(coalesce(c.company, '')) like :q) order by c.fullName asc")
    Page<Contact> search(@Param("ownerId") Long ownerId, @Param("q") String q, Pageable pageable);

    @Query("select distinct c from Contact c join c.tags t where c.ownerId = :ownerId and t.id = :tagId "
            + "and (lower(c.fullName) like :q or lower(c.email) like :q or lower(coalesce(c.company, '')) like :q) "
            + "order by c.fullName asc")
    Page<Contact> searchByTag(@Param("ownerId") Long ownerId, @Param("tagId") Long tagId, @Param("q") String q,
                              Pageable pageable);

    @Query("select c from Contact c where c.ownerId = :ownerId and (lower(c.fullName) like :q or lower(c.email) like :q) "
            + "order by c.fullName asc")
    List<Contact> suggest(@Param("ownerId") Long ownerId, @Param("q") String q, Pageable pageable);

    List<Contact> findTop200ByOwnerIdOrderByFullNameAsc(Long ownerId);
}

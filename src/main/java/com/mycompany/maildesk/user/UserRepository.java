package com.mycompany.maildesk.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRoleAndStatus(UserRole role, UserStatus status);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    Page<User> findAllByOrderByCreatedAtAsc(Pageable pageable);

    @Modifying
    @Query("update User u set u.sessionEpoch = u.sessionEpoch + 1 where u.id = :id")
    int bumpSessionEpoch(@Param("id") Long id);
}

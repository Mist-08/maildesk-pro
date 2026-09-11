package com.mycompany.maildesk.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RateLimitRepository extends JpaRepository<RateLimitEvent, Long> {

    long countByBucketKeyAndOccurredAtAfter(String bucketKey, Instant after);

    @Modifying
    @Query("delete from RateLimitEvent e where e.occurredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

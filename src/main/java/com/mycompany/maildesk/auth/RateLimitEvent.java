package com.mycompany.maildesk.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rate_limit_events")
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_key", nullable = false, length = 200)
    private String bucketKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected RateLimitEvent() {}

    public RateLimitEvent(String bucketKey, Instant occurredAt) {
        this.bucketKey = bucketKey;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getBucketKey() { return bucketKey; }
    public Instant getOccurredAt() { return occurredAt; }
}

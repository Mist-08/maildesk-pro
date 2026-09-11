package com.mycompany.maildesk.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Desafío de verificación en dos pasos. Solo se guarda el HMAC del código (clave externa a la BD),
 * vinculado al usuario, al propósito y a la sesión pendiente que lo originó.
 */
@Entity
@Table(name = "auth_challenges")
public class AuthChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChallengePurpose purpose;

    @Column(name = "code_hmac", nullable = false, length = 128)
    private String codeHmac;

    @Column(name = "session_binding_hash", nullable = false, length = 128)
    private String sessionBindingHash;

    /** Dato adicional del propósito (p. ej. nuevo correo en EMAIL_CHANGE). */
    @Column(length = 320)
    private String payload;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isActive(Instant now) {
        return consumedAt == null && invalidatedAt == null && attempts < maxAttempts && expiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public ChallengePurpose getPurpose() { return purpose; }
    public void setPurpose(ChallengePurpose purpose) { this.purpose = purpose; }
    public String getCodeHmac() { return codeHmac; }
    public void setCodeHmac(String codeHmac) { this.codeHmac = codeHmac; }
    public String getSessionBindingHash() { return sessionBindingHash; }
    public void setSessionBindingHash(String sessionBindingHash) { this.sessionBindingHash = sessionBindingHash; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public String getRequestIp() { return requestIp; }
    public void setRequestIp(String requestIp) { this.requestIp = requestIp; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

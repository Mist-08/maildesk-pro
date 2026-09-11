package com.mycompany.maildesk.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límites de frecuencia persistentes (sobreviven reinicios y se comparten entre instancias).
 * Los eventos nunca se "reinician" al reenviar: la ventana es deslizante y acumulativa.
 */
@Service
public class RateLimitService {

    private final RateLimitRepository repository;

    public RateLimitService(RateLimitRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isLimited(String bucketKey, int max, Duration window) {
        return repository.countByBucketKeyAndOccurredAtAfter(bucketKey, Instant.now().minus(window)) >= max;
    }

    /** Registra el evento en una transacción propia para que persista aunque el flujo falle. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String bucketKey) {
        repository.save(new RateLimitEvent(bucketKey, Instant.now()));
    }

    /** Comprueba y registra en un solo paso; devuelve true si la petición debe rechazarse. */
    public boolean checkAndRecord(String bucketKey, int max, Duration window) {
        if (isLimited(bucketKey, max, window)) {
            return true;
        }
        record(bucketKey);
        return false;
    }

    @Scheduled(fixedDelay = 6, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    @Transactional
    public void purge() {
        repository.deleteOlderThan(Instant.now().minus(Duration.ofDays(2)));
    }
}

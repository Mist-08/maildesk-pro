package com.mycompany.maildesk.audit;

import com.mycompany.maildesk.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de auditoría de acciones relevantes. Nunca recibe contraseñas, códigos, tokens ni
 * cuerpos de correo: solo identificadores y descripciones cortas.
 */
@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Registra usando el usuario autenticado actual (si existe). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, String targetId, String details, String ip) {
        Long actorId = null;
        String actorEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            actorId = user.getId();
            actorEmail = user.getEmail();
        }
        save(actorId, actorEmail, action, targetType, targetId, details, ip);
    }

    /** Registra con actor explícito (flujos sin sesión: inicio de sesión, recuperación, alta). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(Long actorId, String actorEmail, String action, String targetType, String targetId,
                         String details, String ip) {
        save(actorId, actorEmail, action, targetType, targetId, details, ip);
    }

    private void save(Long actorId, String actorEmail, String action, String targetType, String targetId,
                      String details, String ip) {
        AuditEntry entry = new AuditEntry();
        entry.setActorId(actorId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details == null ? null : (details.length() > 1000 ? details.substring(0, 1000) : details));
        entry.setIpAddress(ip);
        repository.save(entry);
    }
}

package com.mycompany.maildesk.mail;

/**
 * Resultado de un intento de transporte. "Aceptado" significa únicamente que el servidor SMTP
 * aceptó el mensaje; no implica entrega al destinatario final.
 */
public record SendOutcome(FailureKind kind, String serverResponse, String error) {

    public enum FailureKind {
        /** El servidor SMTP aceptó el mensaje. */
        NONE,
        /** Problema de configuración o autenticación SMTP: no tiene sentido reintentar sin cambios. */
        CONFIGURATION,
        /** El servidor rechazó el mensaje o los destinatarios: fallo permanente. */
        REJECTED,
        /** No se pudo establecer conexión: reintentable. */
        TRANSIENT,
        /** La conexión se perdió tras enviar datos: resultado incierto, no se reintenta automáticamente. */
        UNCERTAIN
    }

    public static SendOutcome accepted(String serverResponse) {
        return new SendOutcome(FailureKind.NONE, serverResponse, null);
    }

    public static SendOutcome failure(FailureKind kind, String error) {
        return new SendOutcome(kind, null, error);
    }

    public boolean accepted() {
        return kind == FailureKind.NONE;
    }
}

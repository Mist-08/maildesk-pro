package com.mycompany.maildesk.messages;

public enum MessageStatus {
    DRAFT("Borrador", "draft"),
    QUEUED("En cola", "queued"),
    SENDING("Enviando", "sending"),
    /** El servidor SMTP aceptó el mensaje. No garantiza la entrega al destinatario. */
    SENT("Aceptado por el servidor", "sent"),
    FAILED("Fallido", "failed"),
    /** La conexión falló tras transmitir datos: no se sabe si el servidor lo aceptó. */
    UNCERTAIN("Resultado incierto", "uncertain"),
    CANCELLED("Cancelado", "cancelled");

    private final String label;
    private final String css;

    MessageStatus(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() { return label; }
    public String getCss() { return css; }

    public boolean isEditable() { return this == DRAFT; }
    public boolean isCancellable() { return this == QUEUED; }
    public boolean isRetryable() { return this == FAILED; }
    public boolean isDeletable() { return this != QUEUED && this != SENDING; }
}

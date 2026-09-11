package com.mycompany.maildesk.mail;

/** Transporte de correo saliente. Las implementaciones clasifican los fallos y nunca simulan éxito. */
public interface MailTransport {

    SendOutcome send(OutgoingMail mail);

    /** true si existe un servidor SMTP configurado (real o local de pruebas). */
    boolean isConfigured();
}

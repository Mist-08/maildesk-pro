package com.mycompany.maildesk.mail;

import com.mycompany.maildesk.config.AppProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Estado de la configuración de correo para mostrar avisos honestos en la interfaz. */
@Component
public class MailStatus {

    private final AppProperties properties;
    private final JavaMailSenderImpl mailSender;
    private final MailTransport transport;

    public MailStatus(AppProperties properties, JavaMailSenderImpl mailSender, MailTransport transport) {
        this.properties = properties;
        this.mailSender = mailSender;
        this.transport = transport;
    }

    /** true cuando el operador declaró que el SMTP configurado es un servidor real autorizado. */
    public boolean isReal() {
        return properties.getMail().isReal();
    }

    public boolean isConfigured() {
        return transport.isConfigured();
    }

    public String getHostSummary() {
        return mailSender.getHost() + ":" + mailSender.getPort();
    }

    public String getFrom() {
        return properties.getMail().getFrom();
    }
}

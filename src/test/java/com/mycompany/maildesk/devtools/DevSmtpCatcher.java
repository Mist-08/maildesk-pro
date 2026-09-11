package com.mycompany.maildesk.devtools;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.mycompany.maildesk.support.TestData;
import jakarta.mail.internet.MimeMessage;

/**
 * Herramienta de DESARROLLO (no forma parte del producto): servidor SMTP local aislado que recibe los
 * correos de la aplicación (códigos de verificación, invitaciones, mensajes) y los muestra en consola.
 * Nada sale a Internet. Uso: scripts/start-dev-smtp.ps1 (escucha en 127.0.0.1:1025).
 */
public final class DevSmtpCatcher {

    private DevSmtpCatcher() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1025;
        GreenMail server = new GreenMail(new ServerSetup(port, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        server.withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());
        server.start();
        System.out.println("=== MailDesk Pro · SMTP local de desarrollo escuchando en 127.0.0.1:" + port + " ===");
        System.out.println("Los correos recibidos se muestran aquí. Ctrl+C para detener.");
        int shown = 0;
        while (true) {
            server.waitForIncomingEmail(60_000, shown + 1);
            MimeMessage[] messages = server.getReceivedMessages();
            for (; shown < messages.length; shown++) {
                MimeMessage m = messages[shown];
                System.out.println();
                System.out.println("---------------------------------------------------------------");
                System.out.println("Para:    " + String.join(", ", java.util.Arrays.stream(m.getAllRecipients()).map(Object::toString).toList()));
                System.out.println("De:      " + m.getFrom()[0]);
                System.out.println("Asunto:  " + m.getSubject());
                System.out.println("---------------------------------------------------------------");
                String text = TestData.textOf(m);
                System.out.println(text.length() > 3000 ? text.substring(0, 3000) + "…" : text);
            }
        }
    }
}

package com.mycompany.maildesk.mail;

import com.mycompany.maildesk.mail.SendOutcome.FailureKind;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/**
 * Clasifica excepciones del transporte SMTP según la fase en la que ocurrieron. La regla clave:
 * un fallo ocurrido DESPUÉS de empezar a transmitir el mensaje es de resultado incierto y no se
 * reintenta a ciegas (podría duplicar el envío).
 */
public final class SendFailureClassifier {

    private SendFailureClassifier() {}

    public static FailureKind classifyConnectFailure(Exception e) {
        if (contains(e, AuthenticationFailedException.class)) {
            return FailureKind.CONFIGURATION;
        }
        if (contains(e, SSLException.class)) {
            return FailureKind.CONFIGURATION;
        }
        if (contains(e, UnknownHostException.class)) {
            return FailureKind.CONFIGURATION;
        }
        if (contains(e, ConnectException.class) || contains(e, SocketTimeoutException.class)
                || contains(e, IOException.class)) {
            return FailureKind.TRANSIENT;
        }
        return FailureKind.TRANSIENT;
    }

    public static FailureKind classifySendFailure(Exception e) {
        if (contains(e, AddressException.class)) {
            return FailureKind.REJECTED;
        }
        if (contains(e, SendFailedException.class)) {
            return FailureKind.REJECTED;
        }
        if (contains(e, IOException.class)) {
            return FailureKind.UNCERTAIN;
        }
        return FailureKind.UNCERTAIN;
    }

    public static String describe(Exception e) {
        Throwable t = e;
        String last = null;
        int depth = 0;
        while (t != null && depth < 8) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                last = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            t = t.getCause();
            depth++;
        }
        String text = last == null ? e.getClass().getSimpleName() : last;
        text = text.replaceAll("[\\r\\n]+", " ");
        return text.length() > 900 ? text.substring(0, 900) : text;
    }

    private static boolean contains(Throwable e, Class<? extends Throwable> type) {
        Throwable t = e;
        int depth = 0;
        while (t != null && depth < 10) {
            if (type.isInstance(t)) {
                return true;
            }
            t = t.getCause();
            depth++;
        }
        return false;
    }
}

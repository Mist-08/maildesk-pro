package com.mycompany.maildesk.mail;

import com.mycompany.maildesk.common.BusinessException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validación estricta de direcciones y cabeceras: impide inyección de cabeceras (CR/LF), direcciones
 * malformadas y duplicados. Solo se aceptan direcciones simples (sin nombre visible ni grupos).
 */
public final class MailAddressValidator {

    private MailAddressValidator() {}

    public static void rejectHeaderInjection(String value, String fieldLabel) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0)) {
            throw new BusinessException("El campo \"" + fieldLabel + "\" contiene caracteres no permitidos.");
        }
    }

    /** Analiza una lista separada por comas o punto y coma; devuelve direcciones normalizadas. */
    public static List<String> parseList(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        rejectHeaderInjection(raw, fieldLabel);
        Set<String> result = new LinkedHashSet<>();
        for (String part : raw.split("[,;]")) {
            String candidate = part.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            result.add(normalizeSingle(candidate, fieldLabel));
        }
        return new ArrayList<>(result);
    }

    public static String normalizeSingle(String candidate, String fieldLabel) {
        rejectHeaderInjection(candidate, fieldLabel);
        try {
            InternetAddress address = new InternetAddress(candidate, true);
            address.validate();
            String email = address.getAddress();
            if (email == null || !email.contains("@") || email.length() > 320
                    || address.getPersonal() != null || !email.equals(candidate)) {
                throw new BusinessException("Dirección no válida en \"" + fieldLabel + "\": " + shorten(candidate));
            }
            return email.toLowerCase();
        } catch (AddressException e) {
            throw new BusinessException("Dirección no válida en \"" + fieldLabel + "\": " + shorten(candidate));
        }
    }

    public static boolean isValid(String candidate) {
        try {
            normalizeSingle(candidate, "correo");
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}

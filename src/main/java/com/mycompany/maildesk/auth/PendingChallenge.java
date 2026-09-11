package com.mycompany.maildesk.auth;

import java.io.Serializable;
import java.time.Instant;

/**
 * Estado guardado en la sesión HTTP mientras un desafío está pendiente. El token de vinculación
 * es aleatorio y solo su hash se guarda en la base de datos junto al desafío.
 */
public record PendingChallenge(
        Long userId,
        Long challengeId,
        ChallengePurpose purpose,
        String bindingToken,
        String maskedEmail,
        Instant issuedAt) implements Serializable {

    public static final String LOGIN_KEY = "MAILDESK_PENDING_LOGIN";
    public static final String SETUP_KEY = "MAILDESK_PENDING_SETUP";
    public static final String EMAIL_CHANGE_KEY = "MAILDESK_PENDING_EMAIL_CHANGE";

    public static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }
}

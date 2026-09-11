package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.BusinessException;

/** Política de contraseñas de la aplicación (independiente de las credenciales del buzón). */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    public static void validate(String password, String confirm, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BusinessException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BusinessException("La contraseña no puede superar " + MAX_LENGTH + " caracteres.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean other = password.chars().anyMatch(c -> !Character.isLetter(c));
        if (!letter || !other) {
            throw new BusinessException("La contraseña debe combinar letras con números o símbolos.");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw new BusinessException("La contraseña no puede ser igual al correo.");
        }
        if (confirm != null && !password.equals(confirm)) {
            throw new BusinessException("Las contraseñas no coinciden.");
        }
    }
}

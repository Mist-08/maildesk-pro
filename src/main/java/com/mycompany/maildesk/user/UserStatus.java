package com.mycompany.maildesk.user;

public enum UserStatus {
    /** Cuenta creada pero correo aún no verificado (alta inicial del administrador). */
    PENDING_VERIFICATION("Pendiente de verificación"),
    ACTIVE("Activo"),
    DISABLED("Deshabilitado");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

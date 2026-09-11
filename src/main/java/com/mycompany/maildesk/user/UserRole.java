package com.mycompany.maildesk.user;

public enum UserRole {
    ADMIN("Administrador"),
    USER("Usuario");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.mycompany.maildesk.common;

/** Recurso inexistente o no perteneciente al usuario actual (se responde 404 para no filtrar existencia). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

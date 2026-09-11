package com.mycompany.maildesk.common;

/** Error de negocio con mensaje apto para mostrarse al usuario (sin detalles internos). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}

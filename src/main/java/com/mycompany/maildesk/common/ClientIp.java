package com.mycompany.maildesk.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Obtiene la IP del cliente. Con {@code server.forward-headers-strategy=native} (producción tras
 * proxy) Tomcat ya resuelve X-Forwarded-For; aquí solo normalizamos y acotamos la longitud.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            return "desconocida";
        }
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}

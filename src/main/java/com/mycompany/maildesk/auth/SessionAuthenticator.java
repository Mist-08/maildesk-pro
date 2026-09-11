package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Establece la sesión autenticada una vez completada la verificación en dos pasos: renueva el
 * identificador de sesión, limpia el estado pendiente y persiste el contexto de seguridad.
 */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public void establish(HttpServletRequest request, HttpServletResponse response, User user) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PendingChallenge.LOGIN_KEY);
            session.removeAttribute(PendingChallenge.SETUP_KEY);
        }
        request.changeSessionId();
        AuthenticatedUser principal = new AuthenticatedUser(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    /** Refresca el principal en sesión (p. ej. tras cambiar nombre o correo) sin alterar la sesión. */
    public void refresh(HttpServletRequest request, HttpServletResponse response, User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    public void terminate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holderStrategy.clearContext();
    }
}

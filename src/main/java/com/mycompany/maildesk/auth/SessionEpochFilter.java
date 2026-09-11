package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revoca sesiones cuya "época" ya no coincide con la del usuario (cambio de contraseña,
 * restablecimiento, deshabilitación). También expulsa cuentas deshabilitadas.
 */
public class SessionEpochFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public SessionEpochFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            Optional<User> current = users.findById(principal.getId());
            boolean valid = current.isPresent()
                    && current.get().getStatus() == UserStatus.ACTIVE
                    && current.get().getSessionEpoch() == principal.getSessionEpoch();
            if (!valid) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login?revoked");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.equals("/favicon.ico");
    }
}

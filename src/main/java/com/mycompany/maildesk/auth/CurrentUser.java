package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.NotFoundException;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acceso al usuario autenticado actual. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new AccessDeniedException("Sesión no autenticada"));
    }

    public Long id() {
        return require().getId();
    }

    public User load() {
        return users.findById(id()).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}

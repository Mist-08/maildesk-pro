package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRole;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Principal almacenado en la sesión tras completar la verificación en dos pasos. */
public class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String displayName;
    private final UserRole role;
    private final int sessionEpoch;

    public AuthenticatedUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.sessionEpoch = user.getSessionEpoch();
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public int getSessionEpoch() { return sessionEpoch; }

    @Override
    public String toString() {
        return email;
    }
}

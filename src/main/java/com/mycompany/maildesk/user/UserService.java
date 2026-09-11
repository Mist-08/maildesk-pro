package com.mycompany.maildesk.user;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.NotFoundException;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public boolean activeAdminExists() {
        return users.existsByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
    }

    @Transactional
    public User create(String email, String displayName, String rawPassword, UserRole role, UserStatus status) {
        String normalized = email.strip().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new BusinessException("Ya existe una cuenta con ese correo.");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setDisplayName(cleanName(displayName));
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return users.save(user);
    }

    /** Cambia la contraseña y revoca todas las sesiones existentes (nueva época de sesión). */
    @Transactional
    public User updatePasswordAndRevokeSessions(Long userId, String rawPassword) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setSessionEpoch(user.getSessionEpoch() + 1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        return users.save(user);
    }

    @Transactional
    public User setStatus(Long userId, UserStatus status, Long actorId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (user.getId().equals(actorId)) {
            throw new BusinessException("No puedes cambiar el estado de tu propia cuenta.");
        }
        if (status == UserStatus.DISABLED && user.isAdmin()
                && users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessException("Debe permanecer al menos un administrador activo.");
        }
        user.setStatus(status);
        user.setSessionEpoch(user.getSessionEpoch() + 1);
        return users.save(user);
    }

    @Transactional
    public User setRole(Long userId, UserRole role, Long actorId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (user.getId().equals(actorId)) {
            throw new BusinessException("No puedes cambiar tu propio rol.");
        }
        if (role == UserRole.USER && user.isAdmin() && user.getStatus() == UserStatus.ACTIVE
                && users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessException("Debe permanecer al menos un administrador activo.");
        }
        user.setRole(role);
        user.setSessionEpoch(user.getSessionEpoch() + 1);
        return users.save(user);
    }

    public static String cleanName(String displayName) {
        if (displayName == null) {
            throw new BusinessException("El nombre es obligatorio.");
        }
        String name = displayName.strip().replaceAll("[\\r\\n\\t\\p{Cntrl}]", "");
        if (name.length() < 2 || name.length() > 120) {
            throw new BusinessException("El nombre debe tener entre 2 y 120 caracteres.");
        }
        return name;
    }
}

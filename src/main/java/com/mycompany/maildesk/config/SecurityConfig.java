package com.mycompany.maildesk.config;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.auth.AuthenticatedUser;
import com.mycompany.maildesk.auth.SessionEpochFilter;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuración de seguridad web. El inicio de sesión no usa el formulario estándar: lo gestiona
 * {@code AuthController} en dos pasos (contraseña y código por correo). Hasta completar el segundo
 * paso el usuario NO está autenticado para Spring Security, por lo que ninguna ruta protegida es
 * accesible.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id con los parámetros recomendados por Spring Security (memoria 16 MiB, 2 iteraciones).
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, AuditService audit)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/error", "/error/**",
                        "/login", "/login/**", "/setup", "/setup/**", "/invite/**", "/password/**",
                        "/branding/logo", "/actuator/health").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("MDSESSION")
                .addLogoutHandler((request, response, authentication) -> {
                    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser u) {
                        audit.recordAs(u.getId(), u.getEmail(), "LOGOUT", "USER", String.valueOf(u.getId()),
                                null, ClientIp.of(request));
                    }
                }))
            .sessionManagement(session -> session.sessionFixation().changeSessionId())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; font-src 'self'; object-src 'none'; frame-ancestors 'none'; "
                        + "form-action 'self'; base-uri 'self'; frame-src 'self'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")))
            .addFilterAfter(new SessionEpochFilter(users), SecurityContextHolderFilter.class);
        return http.build();
    }
}

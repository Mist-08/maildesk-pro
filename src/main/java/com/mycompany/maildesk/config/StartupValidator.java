package com.mycompany.maildesk.config;

import com.mycompany.maildesk.mail.MailAddressValidator;
import com.mycompany.maildesk.mail.MailStatus;
import com.mycompany.maildesk.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Comprobaciones de arranque: avisa de configuración incompleta y detiene producción insegura. */
@Component
public class StartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final AppProperties properties;
    private final Environment environment;
    private final UserService userService;
    private final MailStatus mailStatus;

    public StartupValidator(AppProperties properties, Environment environment, UserService userService,
                            MailStatus mailStatus) {
        this.properties = properties;
        this.environment = environment;
        this.userService = userService;
        this.mailStatus = mailStatus;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = environment.matchesProfiles("prod");
        boolean adminExists = userService.activeAdminExists();

        if (!adminExists) {
            String email = properties.getInitialAdminEmail();
            if (email == null || email.isBlank() || !MailAddressValidator.isValid(email.strip())) {
                fail(prod, "APP_INITIAL_ADMIN_EMAIL no está definido o no es válido; el alta inicial no podrá completarse.");
            }
            if (properties.getSetupSecret() == null || properties.getSetupSecret().strip().length() < 16) {
                fail(prod, "APP_SETUP_SECRET no está definido (mínimo 16 caracteres); el alta inicial no podrá completarse.");
            } else {
                log.info("Alta inicial pendiente: visite {}/setup con el secreto de instalación.", properties.getBaseUrl());
            }
        }
        if (!mailStatus.isConfigured()) {
            fail(prod, "Correo saliente sin configurar (MAIL_HOST / MAIL_FROM). Los códigos de verificación no podrán enviarse.");
        } else if (!mailStatus.isReal()) {
            log.warn("Correo real pendiente de configuración: se usará el SMTP local {} (APP_MAIL_REAL=false).",
                    mailStatus.getHostSummary());
        }
        if (prod && properties.getBaseUrl().startsWith("http://") && !properties.getBaseUrl().contains("localhost")) {
            log.warn("APP_BASE_URL usa http:// en producción; se recomienda HTTPS.");
        }
    }

    private static void fail(boolean prod, String message) {
        if (prod) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }
}

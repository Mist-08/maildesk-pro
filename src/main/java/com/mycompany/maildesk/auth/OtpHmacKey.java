package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Clave HMAC para los códigos de verificación. Vive fuera de la base de datos: proviene de la
 * variable {@code APP_OTP_HMAC_SECRET} o, solo en perfiles de desarrollo, de un archivo local
 * generado automáticamente (excluido del repositorio).
 */
@Component
public class OtpHmacKey {

    private static final Logger log = LoggerFactory.getLogger(OtpHmacKey.class);

    private final byte[] key;

    public OtpHmacKey(AppProperties properties) {
        String secret = properties.getOtpHmacSecret();
        if (secret != null && !secret.isBlank()) {
            if (secret.length() < 32) {
                throw new IllegalStateException("APP_OTP_HMAC_SECRET debe tener al menos 32 caracteres");
            }
            this.key = secret.getBytes(StandardCharsets.UTF_8);
            return;
        }
        if (!properties.isAllowGeneratedOtpKey()) {
            throw new IllegalStateException("Falta APP_OTP_HMAC_SECRET (obligatoria en este perfil). "
                    + "Genere un valor aleatorio de al menos 32 caracteres y defínalo en el entorno o en .env.");
        }
        this.key = loadOrCreate(Path.of(properties.getOtpHmacKeyFile()));
    }

    private static byte[] loadOrCreate(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                return Base64.getDecoder().decode(Files.readString(file, StandardCharsets.UTF_8).strip());
            }
            byte[] generated = new byte[48];
            Hashing.secureRandom().nextBytes(generated);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, Base64.getEncoder().encodeToString(generated), StandardCharsets.UTF_8);
            log.warn("APP_OTP_HMAC_SECRET no definida: se generó una clave de desarrollo en {}", file.toAbsolutePath());
            return generated;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar la clave HMAC en " + file, e);
        }
    }

    public String hmac(Long challengeId, String code) {
        return Hashing.hmacSha256Hex(key, challengeId + ":" + code);
    }
}

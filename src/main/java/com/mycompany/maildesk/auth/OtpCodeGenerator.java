package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.Hashing;
import org.springframework.stereotype.Component;

/** Genera códigos numéricos de ocho dígitos con SecureRandom (ceros a la izquierda incluidos). */
@Component
public class OtpCodeGenerator {

    public static final int LENGTH = 8;

    public String generate() {
        int value = Hashing.secureRandom().nextInt(100_000_000);
        return String.format("%08d", value);
    }
}

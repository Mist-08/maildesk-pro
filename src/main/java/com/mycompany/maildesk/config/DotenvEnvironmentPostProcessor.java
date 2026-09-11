package com.mycompany.maildesk.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Carga explícita del archivo {@code .env} del directorio de trabajo (o de la ruta indicada por
 * {@code APP_DOTENV_PATH}). Las variables de entorno reales y las propiedades del sistema tienen
 * prioridad sobre el contenido de .env: este archivo solo rellena lo que falte.
 *
 * <p>Formato admitido: {@code CLAVE=valor}, comentarios con {@code #}, valores entre comillas
 * simples o dobles, y el prefijo opcional {@code export}. No se evalúan expresiones.</p>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "dotenvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configured = System.getenv("APP_DOTENV_PATH");
        Path path = Path.of(configured != null && !configured.isBlank() ? configured : ".env");
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Map<String, Object> values = parse(Files.readAllLines(path, StandardCharsets.UTF_8));
            values.keySet().removeIf(k -> System.getenv(k) != null || System.getProperty(k) != null);
            if (!values.isEmpty()) {
                // Después de systemEnvironment: las variables reales ganan.
                environment.getPropertySources().addAfter("systemEnvironment", new MapPropertySource(SOURCE_NAME, values));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo .env: " + path.toAbsolutePath(), e);
        }
    }

    public static Map<String, Object> parse(List<String> lines) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring(7).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            } else {
                int hash = value.indexOf(" #");
                if (hash >= 0) {
                    value = value.substring(0, hash).strip();
                }
            }
            if (key.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
                result.put(key, value);
            }
        }
        return result;
    }
}

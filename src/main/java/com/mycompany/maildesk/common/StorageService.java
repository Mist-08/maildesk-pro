package com.mycompany.maildesk.common;

import com.mycompany.maildesk.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Almacenamiento de archivos (adjuntos, logotipo) fuera de los recursos públicos. Los nombres de
 * archivo son generados internamente; el nombre original nunca se usa en el sistema de archivos.
 */
@Component
public class StorageService {

    private final Path root;

    public StorageService(AppProperties properties) {
        this.root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
    }

    public String store(String area, InputStream data) throws IOException {
        String storedName = UUID.randomUUID().toString().replace("-", "") + ".bin";
        Path target = resolve(area, storedName);
        Files.createDirectories(target.getParent());
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    public Path resolve(String area, String storedName) {
        if (!storedName.matches("[a-f0-9]{32}\\.bin")) {
            throw new IllegalArgumentException("Nombre interno inválido");
        }
        if (!area.matches("[a-z0-9\\-]{1,40}(/[0-9]{1,19})?")) {
            throw new IllegalArgumentException("Área inválida");
        }
        Path resolved = root.resolve(area).resolve(storedName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Ruta fuera del almacenamiento");
        }
        return resolved;
    }

    public void delete(String area, String storedName) {
        try {
            Files.deleteIfExists(resolve(area, storedName));
        } catch (IOException ignored) {
            // Un archivo huérfano no compromete la coherencia de la base de datos.
        }
    }

    public Path root() {
        return root;
    }
}

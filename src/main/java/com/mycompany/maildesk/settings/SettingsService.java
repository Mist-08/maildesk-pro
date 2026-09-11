package com.mycompany.maildesk.settings;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.HtmlSanitizer;
import com.mycompany.maildesk.common.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personalización del negocio (nombre comercial, logotipo, firma predeterminada, color) sin tocar código. */
@Service
public class SettingsService {

    public static final String BUSINESS_NAME = "business_name";
    public static final String LOGO_FILE = "logo_file";
    public static final String LOGO_CONTENT_TYPE = "logo_content_type";
    public static final String DEFAULT_SIGNATURE = "default_signature_html";
    public static final String ACCENT_COLOR = "accent_color";
    public static final String SETUP_COMPLETED = "setup_completed";

    private static final String LOGO_AREA = "branding";

    private final AppSettingRepository repository;
    private final StorageService storage;
    private final HtmlSanitizer sanitizer;

    public SettingsService(AppSettingRepository repository, StorageService storage, HtmlSanitizer sanitizer) {
        this.repository = repository;
        this.storage = storage;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findById(key).map(AppSetting::getValue).filter(v -> v != null && !v.isBlank());
    }

    @Transactional(readOnly = true)
    public String getBusinessName() {
        return get(BUSINESS_NAME).orElse("MailDesk Pro");
    }

    @Transactional(readOnly = true)
    public String getAccentColor() {
        return get(ACCENT_COLOR).orElse("#4f46e5");
    }

    @Transactional(readOnly = true)
    public String getDefaultSignature() {
        return get(DEFAULT_SIGNATURE).orElse("");
    }

    @Transactional(readOnly = true)
    public boolean hasLogo() {
        return get(LOGO_FILE).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isSetupCompleted() {
        return get(SETUP_COMPLETED).map("true"::equals).orElse(false);
    }

    @Transactional
    public void set(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, null));
        setting.setValue(value);
        repository.save(setting);
    }

    @Transactional
    public void updateBranding(String businessName, String accentColor, String defaultSignatureHtml) {
        if (businessName == null || businessName.strip().length() < 2 || businessName.strip().length() > 80) {
            throw new BusinessException("El nombre comercial debe tener entre 2 y 80 caracteres.");
        }
        if (accentColor != null && !accentColor.isBlank() && !accentColor.matches("#[0-9a-fA-F]{6}")) {
            throw new BusinessException("El color debe expresarse en formato hexadecimal (#RRGGBB).");
        }
        set(BUSINESS_NAME, businessName.strip());
        set(ACCENT_COLOR, accentColor == null || accentColor.isBlank() ? "#4f46e5" : accentColor.toLowerCase());
        set(DEFAULT_SIGNATURE, sanitizer.sanitize(defaultSignatureHtml));
    }

    @Transactional
    public void storeLogo(InputStream data, String contentType, long size, byte[] head) throws IOException {
        if (size <= 0 || size > 1_048_576) {
            throw new BusinessException("El logotipo debe pesar entre 1 byte y 1 MB.");
        }
        String detected = detectImageType(head);
        if (detected == null) {
            throw new BusinessException("El logotipo debe ser una imagen PNG, JPEG, GIF o WebP válida.");
        }
        get(LOGO_FILE).ifPresent(old -> storage.delete(LOGO_AREA, old));
        String stored = storage.store(LOGO_AREA, data);
        set(LOGO_FILE, stored);
        set(LOGO_CONTENT_TYPE, detected);
    }

    @Transactional
    public void removeLogo() {
        get(LOGO_FILE).ifPresent(old -> storage.delete(LOGO_AREA, old));
        set(LOGO_FILE, "");
        set(LOGO_CONTENT_TYPE, "");
    }

    @Transactional(readOnly = true)
    public Optional<LogoFile> logo() {
        return get(LOGO_FILE).map(file -> new LogoFile(storage.resolve(LOGO_AREA, file),
                get(LOGO_CONTENT_TYPE).orElse("image/png")));
    }

    public record LogoFile(java.nio.file.Path path, String contentType) {}

    /** Detección por firma binaria (magic bytes); el tipo declarado por el navegador no se usa. */
    public static String detectImageType(byte[] head) {
        if (head == null || head.length < 12) {
            return null;
        }
        if (startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        if (startsWith(head, "GIF87a".getBytes()) || startsWith(head, "GIF89a".getBytes())) {
            return "image/gif";
        }
        if (startsWith(head, "RIFF".getBytes()) && Arrays.equals(Arrays.copyOfRange(head, 8, 12), "WEBP".getBytes())) {
            return "image/webp";
        }
        return null;
    }

    static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

package com.mycompany.maildesk.settings;

import com.mycompany.maildesk.common.NotFoundException;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sirve el logotipo del negocio (público para poder mostrarlo en la pantalla de acceso). */
@Controller
public class BrandingController {

    private final SettingsService settings;

    public BrandingController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<Resource> logo() {
        SettingsService.LogoFile logo = settings.logo().orElseThrow(() -> new NotFoundException("Sin logotipo"));
        if (!Files.isRegularFile(logo.path())) {
            throw new NotFoundException("Sin logotipo");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(new FileSystemResource(logo.path()));
    }
}

package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.contacts.ContactService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints JSON mínimos para autoguardado de borradores y sugerencia de contactos (protegidos por sesión y CSRF). */
@RestController
@RequestMapping("/api")
public class DraftApiController {

    private final MessageService messages;
    private final ContactService contacts;
    private final CurrentUser currentUser;

    public DraftApiController(MessageService messages, ContactService contacts, CurrentUser currentUser) {
        this.messages = messages;
        this.contacts = contacts;
        this.currentUser = currentUser;
    }

    @PutMapping("/drafts/{id}")
    public ResponseEntity<Map<String, Object>> autosave(@PathVariable Long id, @RequestBody DraftForm form) {
        Message saved = messages.saveDraft(id, currentUser.id(), form);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "savedAt", Instant.now().toString(),
                "status", saved.getStatus().name()));
    }

    @GetMapping("/contacts/suggest")
    public List<Map<String, String>> suggest(@RequestParam(defaultValue = "") String q) {
        return contacts.suggest(currentUser.id(), q).stream()
                .map(c -> Map.of("name", c.getFullName(), "email", c.getEmail()))
                .toList();
    }
}

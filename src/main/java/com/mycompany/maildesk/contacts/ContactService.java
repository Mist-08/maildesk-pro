package com.mycompany.maildesk.contacts;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.NotFoundException;
import com.mycompany.maildesk.mail.MailAddressValidator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactRepository contacts;
    private final TagRepository tags;
    private final AuditService audit;

    public ContactService(ContactRepository contacts, TagRepository tags, AuditService audit) {
        this.contacts = contacts;
        this.tags = tags;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Contact> list(Long ownerId, String q, Long tagId, Pageable pageable) {
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase()) + "%";
        if (tagId != null) {
            tags.findByIdAndOwnerId(tagId, ownerId).orElseThrow(() -> new NotFoundException("Etiqueta no encontrada"));
            return contacts.searchByTag(ownerId, tagId, pattern, pageable);
        }
        return contacts.search(ownerId, pattern, pageable);
    }

    @Transactional(readOnly = true)
    public List<Contact> suggest(Long ownerId, String q) {
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase()) + "%";
        return contacts.suggest(ownerId, pattern, PageRequest.of(0, 8));
    }

    @Transactional(readOnly = true)
    public List<Contact> allForPicker(Long ownerId) {
        return contacts.findTop200ByOwnerIdOrderByFullNameAsc(ownerId);
    }

    @Transactional(readOnly = true)
    public Contact getOwned(Long id, Long ownerId) {
        return contacts.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new NotFoundException("Contacto no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Tag> tagsOf(Long ownerId) {
        return tags.findByOwnerIdOrderByNameAsc(ownerId);
    }

    @Transactional
    public Contact save(Long ownerId, Long id, String fullName, String email, String company, String phone,
                        String notes, List<Long> tagIds, String ip) {
        Contact contact = id == null ? new Contact() : getOwned(id, ownerId);
        contact.setOwnerId(ownerId);
        contact.setFullName(requireLength(fullName, "nombre", 1, 160));
        contact.setEmail(MailAddressValidator.normalizeSingle(email == null ? "" : email.strip(), "correo"));
        contact.setCompany(optional(company, 160));
        contact.setPhone(optional(phone, 40));
        contact.setNotes(optional(notes, 2000));
        Set<Tag> selected = new LinkedHashSet<>();
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                tags.findByIdAndOwnerId(tagId, ownerId).ifPresent(selected::add);
            }
        }
        contact.setTags(selected);
        Contact saved = contacts.save(contact);
        audit.record(id == null ? "CONTACT_CREATED" : "CONTACT_UPDATED", "CONTACT", String.valueOf(saved.getId()), null, ip);
        return saved;
    }

    @Transactional
    public void delete(Long id, Long ownerId, String ip) {
        Contact contact = getOwned(id, ownerId);
        contacts.delete(contact);
        audit.record("CONTACT_DELETED", "CONTACT", String.valueOf(id), null, ip);
    }

    @Transactional
    public Tag createTag(Long ownerId, String name, String color) {
        String clean = requireLength(name, "nombre de etiqueta", 1, 60);
        if (tags.existsByOwnerIdAndNameIgnoreCase(ownerId, clean)) {
            throw new BusinessException("Ya existe una etiqueta con ese nombre.");
        }
        Tag tag = new Tag();
        tag.setOwnerId(ownerId);
        tag.setName(clean);
        tag.setColor(validColor(color));
        return tags.save(tag);
    }

    @Transactional
    public void deleteTag(Long tagId, Long ownerId) {
        Tag tag = tags.findByIdAndOwnerId(tagId, ownerId).orElseThrow(() -> new NotFoundException("Etiqueta no encontrada"));
        tags.delete(tag);
    }

    private static String requireLength(String value, String label, int min, int max) {
        String v = value == null ? "" : value.strip().replaceAll("[\\p{Cntrl}]", "");
        if (v.length() < min || v.length() > max) {
            throw new BusinessException("El campo \"" + label + "\" debe tener entre " + min + " y " + max + " caracteres.");
        }
        return v;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max) {
            throw new BusinessException("Un campo supera la longitud máxima (" + max + ").");
        }
        return v;
    }

    private static String validColor(String color) {
        if (color == null || color.isBlank()) {
            return "#6366f1";
        }
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            throw new BusinessException("Color no válido.");
        }
        return color.toLowerCase();
    }
}

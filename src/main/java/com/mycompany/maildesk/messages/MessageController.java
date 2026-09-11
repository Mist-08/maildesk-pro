package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.contacts.Contact;
import com.mycompany.maildesk.contacts.ContactService;
import com.mycompany.maildesk.templates.MailTemplate;
import com.mycompany.maildesk.templates.TemplateRenderer;
import com.mycompany.maildesk.templates.TemplateService;
import com.mycompany.maildesk.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/messages")
public class MessageController {

    private static final Map<String, String> BOX_TITLES = Map.of(
            "drafts", "Borradores", "outbox", "Bandeja de salida", "sent", "Enviados",
            "failed", "Fallidos e inciertos", "all", "Todos los mensajes");

    private final MessageService messages;
    private final AttachmentService attachments;
    private final ContactService contacts;
    private final TemplateService templates;
    private final CurrentUser currentUser;

    public MessageController(MessageService messages, AttachmentService attachments, ContactService contacts,
                             TemplateService templates, CurrentUser currentUser) {
        this.messages = messages;
        this.attachments = attachments;
        this.contacts = contacts;
        this.templates = templates;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "all") String box, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        String safeBox = BOX_TITLES.containsKey(box) ? box : "all";
        Page<Message> result = messages.list(currentUser.id(), safeBox, q, PageRequest.of(Math.max(0, page), 20));
        model.addAttribute("box", safeBox);
        model.addAttribute("boxTitle", BOX_TITLES.get(safeBox));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("page", result);
        return "messages/list";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) Long contactId, @RequestParam(required = false) Long templateId,
                         RedirectAttributes flash) {
        Long ownerId = currentUser.id();
        Message draft = messages.createDraft(ownerId);
        if (contactId != null) {
            Contact contact = contacts.getOwned(contactId, ownerId);
            messages.setRecipient(draft.getId(), ownerId, contact.getEmail());
        }
        if (templateId != null) {
            applyTemplateInternal(draft.getId(), templateId, contactId, ownerId);
        }
        return "redirect:/messages/" + draft.getId() + "/edit";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Message message = messages.getOwned(id, currentUser.id());
        if (!message.getStatus().isEditable()) {
            return "redirect:/messages/" + id;
        }
        populateCompose(model, message, DraftForm.from(message));
        return "messages/compose";
    }

    @PostMapping("/{id}/save")
    public String save(@PathVariable Long id, @ModelAttribute DraftForm form, RedirectAttributes flash, Model model) {
        try {
            messages.saveDraft(id, currentUser.id(), form);
            flash.addFlashAttribute("success", "Borrador guardado.");
            return "redirect:/messages/" + id + "/edit";
        } catch (BusinessException e) {
            Message message = messages.getOwned(id, currentUser.id());
            model.addAttribute("error", e.getMessage());
            populateCompose(model, message, form);
            return "messages/compose";
        }
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id, @ModelAttribute DraftForm form, HttpServletRequest request,
                       RedirectAttributes flash, Model model) {
        try {
            Message sent = messages.send(id, currentUser.id(), form, ClientIp.of(request));
            flash.addFlashAttribute("success", sent.getStatus() == MessageStatus.QUEUED
                    ? "Mensaje puesto en la cola de salida." : "El mensaje ya estaba en proceso.");
            return "redirect:/messages/" + id;
        } catch (BusinessException e) {
            Message message = messages.getOwned(id, currentUser.id());
            model.addAttribute("error", e.getMessage());
            populateCompose(model, message, form);
            return "messages/compose";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Message message = messages.getOwned(id, currentUser.id());
        model.addAttribute("message", message);
        model.addAttribute("attachments", attachments.listFor(id));
        return "messages/view";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        boolean done = messages.cancel(id, currentUser.id(), ClientIp.of(request));
        flash.addFlashAttribute(done ? "success" : "error", done ? "Envío cancelado." : "El mensaje ya no estaba en cola.");
        return "redirect:/messages/" + id;
    }

    @PostMapping("/{id}/retry")
    public String retry(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        boolean done = messages.retry(id, currentUser.id(), ClientIp.of(request));
        flash.addFlashAttribute(done ? "success" : "error", done ? "Mensaje reenviado a la cola." : "Este mensaje no puede reintentarse.");
        return "redirect:/messages/" + id;
    }

    @PostMapping("/{id}/duplicate")
    public String duplicate(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        Message copy = messages.duplicate(id, currentUser.id(), ClientIp.of(request));
        flash.addFlashAttribute("success", "Se creó un borrador a partir del mensaje.");
        return "redirect:/messages/" + copy.getId() + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        try {
            messages.delete(id, currentUser.id(), ClientIp.of(request));
            flash.addFlashAttribute("success", "Mensaje eliminado.");
            return "redirect:/messages?box=drafts";
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages/" + id;
        }
    }

    @PostMapping("/{id}/apply-template")
    public String applyTemplate(@PathVariable Long id, @RequestParam Long templateId,
                                @RequestParam(required = false) Long contactId, RedirectAttributes flash) {
        try {
            applyTemplateInternal(id, templateId, contactId, currentUser.id());
            flash.addFlashAttribute("success", "Plantilla aplicada al borrador.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/messages/" + id + "/edit";
    }

    private void applyTemplateInternal(Long messageId, Long templateId, Long contactId, Long ownerId) {
        MailTemplate template = templates.getOwned(templateId, ownerId);
        Contact contact = contactId == null ? null : contacts.getOwned(contactId, ownerId);
        User sender = currentUser.load();
        TemplateRenderer.Rendered rendered = templates.render(template, contact, sender);
        messages.applyTemplate(messageId, ownerId, rendered.subject(), rendered.bodyHtml());
        if (contact != null) {
            messages.setRecipient(messageId, ownerId, contact.getEmail());
        }
    }

    @PostMapping("/{id}/attachments")
    public String upload(@PathVariable Long id, @RequestParam("file") MultipartFile file, HttpServletRequest request,
                         RedirectAttributes flash) {
        try {
            attachments.add(id, currentUser.id(), file, ClientIp.of(request));
            flash.addFlashAttribute("success", "Adjunto agregado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            flash.addFlashAttribute("error", "No se pudo guardar el archivo.");
        }
        return "redirect:/messages/" + id + "/edit";
    }

    @PostMapping("/{id}/attachments/{attachmentId}/delete")
    public String removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId, HttpServletRequest request,
                                   RedirectAttributes flash) {
        try {
            attachments.remove(attachmentId, currentUser.id(), ClientIp.of(request));
            flash.addFlashAttribute("success", "Adjunto eliminado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/messages/" + id + "/edit";
    }

    private void populateCompose(Model model, Message message, DraftForm form) {
        Long ownerId = currentUser.id();
        if (form.getSubmissionToken() == null || form.getSubmissionToken().isBlank()) {
            form.setSubmissionToken(UUID.randomUUID().toString());
        }
        model.addAttribute("message", message);
        model.addAttribute("form", form);
        model.addAttribute("attachments", attachments.listFor(message.getId()));
        model.addAttribute("templates", templates.all(ownerId));
        model.addAttribute("contacts", contacts.allForPicker(ownerId));
        model.addAttribute("allowedExtensions", AttachmentValidator.allowedExtensionsLabel());
    }
}

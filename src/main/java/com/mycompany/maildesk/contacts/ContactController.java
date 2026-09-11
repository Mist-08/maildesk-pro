package com.mycompany.maildesk.contacts;

import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/contacts")
public class ContactController {

    private final ContactService contacts;
    private final CurrentUser currentUser;

    public ContactController(ContactService contacts, CurrentUser currentUser) {
        this.contacts = contacts;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) Long tag,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Long ownerId = currentUser.id();
        model.addAttribute("page", contacts.list(ownerId, q, tag, PageRequest.of(Math.max(0, page), 20)));
        model.addAttribute("tags", contacts.tagsOf(ownerId));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("tag", tag);
        return "contacts/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("contact", new Contact());
        model.addAttribute("tags", contacts.tagsOf(currentUser.id()));
        model.addAttribute("selectedTags", List.of());
        return "contacts/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Contact contact = contacts.getOwned(id, currentUser.id());
        model.addAttribute("contact", contact);
        model.addAttribute("tags", contacts.tagsOf(currentUser.id()));
        model.addAttribute("selectedTags", contact.getTags().stream().map(Tag::getId).toList());
        return "contacts/form";
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @RequestParam String fullName, @RequestParam String email,
                       @RequestParam(required = false) String company, @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String notes,
                       @RequestParam(name = "tagIds", required = false) List<Long> tagIds,
                       HttpServletRequest request, RedirectAttributes flash, Model model) {
        try {
            contacts.save(currentUser.id(), id, fullName, email, company, phone, notes, tagIds, ClientIp.of(request));
            flash.addFlashAttribute("success", id == null ? "Contacto creado." : "Contacto actualizado.");
            return "redirect:/contacts";
        } catch (BusinessException e) {
            Contact contact = id == null ? new Contact() : contacts.getOwned(id, currentUser.id());
            contact.setFullName(fullName);
            contact.setEmail(email);
            contact.setCompany(company);
            contact.setPhone(phone);
            contact.setNotes(notes);
            model.addAttribute("contact", contact);
            model.addAttribute("tags", contacts.tagsOf(currentUser.id()));
            model.addAttribute("selectedTags", tagIds == null ? List.of() : tagIds);
            model.addAttribute("error", e.getMessage());
            return "contacts/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        contacts.delete(id, currentUser.id(), ClientIp.of(request));
        flash.addFlashAttribute("success", "Contacto eliminado.");
        return "redirect:/contacts";
    }

    @PostMapping("/tags")
    public String createTag(@RequestParam String name, @RequestParam(required = false) String color,
                            RedirectAttributes flash) {
        try {
            contacts.createTag(currentUser.id(), name, color);
            flash.addFlashAttribute("success", "Etiqueta creada.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/contacts";
    }

    @PostMapping("/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id, RedirectAttributes flash) {
        contacts.deleteTag(id, currentUser.id());
        flash.addFlashAttribute("success", "Etiqueta eliminada.");
        return "redirect:/contacts";
    }
}

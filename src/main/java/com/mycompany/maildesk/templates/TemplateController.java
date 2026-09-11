package com.mycompany.maildesk.templates;

import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templates;
    private final CurrentUser currentUser;

    public TemplateController(TemplateService templates, CurrentUser currentUser) {
        this.templates = templates;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", templates.list(currentUser.id(), q, PageRequest.of(Math.max(0, page), 20)));
        model.addAttribute("q", q == null ? "" : q);
        return "templates/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("template", new MailTemplate());
        model.addAttribute("variables", TemplateRenderer.ALLOWED);
        return "templates/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        MailTemplate template = templates.getOwned(id, currentUser.id());
        model.addAttribute("template", template);
        model.addAttribute("variables", TemplateRenderer.ALLOWED);
        model.addAttribute("preview", templates.preview(template, currentUser.load()));
        return "templates/form";
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(required = false) Long id, @RequestParam String name, @RequestParam String subject,
                       @RequestParam String bodyHtml, HttpServletRequest request, RedirectAttributes flash, Model model) {
        try {
            MailTemplate saved = templates.save(currentUser.id(), id, name, subject, bodyHtml, ClientIp.of(request));
            flash.addFlashAttribute("success", "Plantilla guardada.");
            return "redirect:/templates/" + saved.getId() + "/edit";
        } catch (BusinessException e) {
            MailTemplate template = new MailTemplate();
            template.setName(name);
            template.setSubject(subject);
            template.setBodyHtml(bodyHtml);
            model.addAttribute("template", template);
            model.addAttribute("templateId", id);
            model.addAttribute("variables", TemplateRenderer.ALLOWED);
            model.addAttribute("error", e.getMessage());
            return "templates/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        templates.delete(id, currentUser.id(), ClientIp.of(request));
        flash.addFlashAttribute("success", "Plantilla eliminada.");
        return "redirect:/templates";
    }
}

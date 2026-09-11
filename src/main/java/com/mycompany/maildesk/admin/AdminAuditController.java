package com.mycompany.maildesk.admin;

import com.mycompany.maildesk.audit.AuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit")
public class AdminAuditController {

    private final AuditRepository audit;

    public AdminAuditController(AuditRepository audit) {
        this.audit = audit;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), 30);
        String term = q == null ? "" : q.strip();
        model.addAttribute("page", term.isEmpty() ? audit.findAllByOrderByCreatedAtDesc(pageable)
                : audit.findByActionContainingIgnoreCaseOrActorEmailContainingIgnoreCaseOrderByCreatedAtDesc(term, term, pageable));
        model.addAttribute("q", term);
        return "admin/audit";
    }
}

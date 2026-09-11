package com.mycompany.maildesk.templates;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Sustitución de variables permitidas con la sintaxis {@code {{nombre}}}. Es una sustitución textual
 * pura: nunca se evalúa código ni expresiones introducidas por usuarios. Las variables desconocidas
 * se dejan vacías.
 */
public final class TemplateRenderer {

    public static final List<String> ALLOWED = List.of("nombre", "email", "empresa", "negocio", "remitente", "fecha");

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z_]{1,30})\\s*\\}\\}");

    private TemplateRenderer() {}

    public record Rendered(String subject, String bodyHtml) {}

    public static Rendered render(String subject, String bodyHtml, Map<String, String> values) {
        Map<String, String> safe = new LinkedHashMap<>();
        for (String key : ALLOWED) {
            safe.put(key, values.getOrDefault(key, ""));
        }
        return new Rendered(replace(subject, safe, false), replace(bodyHtml, safe, true));
    }

    static String replace(String text, Map<String, String> values, boolean html) {
        if (text == null) {
            return "";
        }
        Matcher m = VARIABLE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            String value = values.getOrDefault(key, "");
            String replacement = html ? HtmlUtils.htmlEscape(value) : value.replaceAll("[\\r\\n]", " ");
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}

package com.mycompany.maildesk.common;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Saneamiento de HTML introducido por usuarios (cuerpos, firmas, plantillas) con OWASP Java HTML
 * Sanitizer. Se aplica al guardar, al enviar y al mostrar: nunca se confía en HTML almacenado.
 */
@Component
public class HtmlSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("img", "span", "div", "hr", "br", "font")
                    .allowAttributes("src", "alt", "width", "height").onElements("img")
                    .allowUrlProtocols("https", "http")
                    .allowAttributes("style").globally()
                    .allowStyling()
                    .toFactory());

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return POLICY.sanitize(html);
    }

    /** Convierte texto plano en HTML escapado con saltos de línea preservados. */
    public String textToHtml(String text) {
        if (text == null) {
            return "";
        }
        return "<p>" + HtmlUtils.htmlEscape(text).replace("\r\n", "\n").replace("\n", "<br>") + "</p>";
    }

    /** Aproximación de texto plano a partir de HTML (para la parte text/plain de correos HTML). */
    public String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(text).replaceAll("[ \\t\\x0B\\f]+", " ").strip();
    }
}

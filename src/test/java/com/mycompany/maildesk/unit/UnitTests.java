package com.mycompany.maildesk.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycompany.maildesk.auth.OtpCodeGenerator;
import com.mycompany.maildesk.auth.PasswordPolicy;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.common.HtmlSanitizer;
import com.mycompany.maildesk.config.DotenvEnvironmentPostProcessor;
import com.mycompany.maildesk.mail.MailAddressValidator;
import com.mycompany.maildesk.mail.SendFailureClassifier;
import com.mycompany.maildesk.mail.SendOutcome.FailureKind;
import com.mycompany.maildesk.messages.AttachmentValidator;
import com.mycompany.maildesk.templates.TemplateRenderer;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnitTests {

    @Nested
    class OtpCodes {
        @Test
        void generatesEightDigitsWithLeadingZeros() {
            OtpCodeGenerator generator = new OtpCodeGenerator();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                String code = generator.generate();
                assertThat(code).matches("\\d{8}");
                seen.add(code);
            }
            assertThat(seen.size()).isGreaterThan(450);
        }

        @Test
        void hmacIsDeterministicAndConstantTimeCompareWorks() {
            byte[] key = "clave-de-prueba-suficientemente-larga-123".getBytes();
            assertThat(Hashing.hmacSha256Hex(key, "1:12345678")).isEqualTo(Hashing.hmacSha256Hex(key, "1:12345678"));
            assertThat(Hashing.hmacSha256Hex(key, "1:12345678")).isNotEqualTo(Hashing.hmacSha256Hex(key, "2:12345678"));
            assertThat(Hashing.constantTimeEquals("abc", "abc")).isTrue();
            assertThat(Hashing.constantTimeEquals("abc", "abd")).isFalse();
            assertThat(Hashing.constantTimeEquals(null, "abd")).isFalse();
            assertThat(Hashing.randomToken()).hasSizeGreaterThanOrEqualTo(43);
        }
    }

    @Nested
    class Templates {
        @Test
        void replacesAllowedVariablesOnlyAndEscapesHtml() {
            TemplateRenderer.Rendered r = TemplateRenderer.render("Hola {{nombre}} de {{empresa}}",
                    "<p>{{ nombre }} — {{desconocida}} — {{email}}</p>",
                    Map.of("nombre", "Ana <script>", "empresa", "ACME", "email", "ana@x.test"));
            assertThat(r.subject()).isEqualTo("Hola Ana <script> de ACME");
            assertThat(r.bodyHtml()).isEqualTo("<p>Ana &lt;script&gt; —  — ana@x.test</p>");
        }

        @Test
        void neverEvaluatesExpressions() {
            TemplateRenderer.Rendered r = TemplateRenderer.render("${7*7} #{x} {{nombre}}", "{{nombre}} ${System.exit(1)}",
                    Map.of("nombre", "${1+1}"));
            assertThat(r.subject()).isEqualTo("${7*7} #{x} ${1+1}");
            assertThat(r.bodyHtml()).isEqualTo("${1+1} ${System.exit(1)}");
        }
    }

    @Nested
    class Addresses {
        @Test
        void parsesListsAndNormalizes() {
            assertThat(MailAddressValidator.parseList("Ana@X.test; beto@y.test, ana@x.test", "Para"))
                    .containsExactly("ana@x.test", "beto@y.test");
        }

        @Test
        void rejectsInjectionAndMalformed() {
            assertThatThrownBy(() -> MailAddressValidator.parseList("a@b.test\r\nBcc: c@d.test", "Para"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("no permitidos");
            assertThatThrownBy(() -> MailAddressValidator.normalizeSingle("sin-arroba", "Para")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> MailAddressValidator.normalizeSingle("Nombre <a@b.test>", "Para")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> MailAddressValidator.normalizeSingle("a@b.test, c@d.test", "Para")).isInstanceOf(BusinessException.class);
            assertThat(MailAddressValidator.isValid("valido@dominio.test")).isTrue();
        }
    }

    @Nested
    class Attachments {
        @Test
        void validatesByExtensionAndSignature() {
            assertThat(AttachmentValidator.validate("informe.PDF", "%PDF-1.7".getBytes(), 10).contentType()).isEqualTo("application/pdf");
            assertThat(AttachmentValidator.validate("C:\\ruta\\datos.csv", "a,b\n1,2".getBytes(), 7).safeName()).isEqualTo("datos.csv");
            assertThatThrownBy(() -> AttachmentValidator.validate("script.js", "x".getBytes(), 1)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> AttachmentValidator.validate("pagina.html", "<html>".getBytes(), 6)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> AttachmentValidator.validate("imagen.svg", "<svg>".getBytes(), 5)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> AttachmentValidator.validate("foto.png", "no-es-png".getBytes(), 9)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> AttachmentValidator.validate("texto.txt", new byte[]{1, 0, 2}, 3)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> AttachmentValidator.validate("sinextension", "x".getBytes(), 1)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> AttachmentValidator.validate("vacio.txt", new byte[0], 0)).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    class Sanitizer {
        @Test
        void removesScriptsAndEventHandlersButKeepsFormatting() {
            HtmlSanitizer s = new HtmlSanitizer();
            String out = s.sanitize("<p style=\"color:red\" onclick=\"x()\">Hola <b>mundo</b></p><script>alert(1)</script>"
                    + "<a href=\"javascript:void(0)\">mal</a><a href=\"https://ok.test\">bien</a><img src=\"data:text/html,x\">");
            assertThat(out).contains("<b>mundo</b>").contains("https://ok.test").doesNotContain("script")
                    .doesNotContain("onclick").doesNotContain("javascript:").doesNotContain("data:");
            assertThat(s.htmlToText("<p>Hola<br>mundo</p>")).isEqualTo("Hola\nmundo");
            assertThat(s.textToHtml("a<b\nc")).isEqualTo("<p>a&lt;b<br>c</p>");
        }
    }

    @Nested
    class Failures {
        @Test
        void classifiesByPhase() {
            assertThat(SendFailureClassifier.classifyConnectFailure(new MessagingException("x", new ConnectException("refused"))))
                    .isEqualTo(FailureKind.TRANSIENT);
            assertThat(SendFailureClassifier.classifyConnectFailure(new AuthenticationFailedException("535 bad credentials")))
                    .isEqualTo(FailureKind.CONFIGURATION);
            assertThat(SendFailureClassifier.classifySendFailure(new SendFailedException("550 no such user")))
                    .isEqualTo(FailureKind.REJECTED);
            assertThat(SendFailureClassifier.classifySendFailure(new MessagingException("x", new SocketTimeoutException("read timed out"))))
                    .isEqualTo(FailureKind.UNCERTAIN);
            assertThat(SendFailureClassifier.describe(new MessagingException("línea1\r\nlínea2"))).doesNotContain("\n");
        }
    }

    @Nested
    class Passwords {
        @Test
        void enforcesPolicy() {
            assertThatThrownBy(() -> PasswordPolicy.validate("corta1", "corta1", "a@b.test")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> PasswordPolicy.validate("soloLetrasAqui", "soloLetrasAqui", "a@b.test")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> PasswordPolicy.validate("Clave-Segura-1", "Otra-Clave-1", "a@b.test")).isInstanceOf(BusinessException.class);
            PasswordPolicy.validate("Clave-Segura-1", "Clave-Segura-1", "a@b.test");
        }
    }

    @Nested
    class Dotenv {
        @Test
        void parsesKeyValuesQuotesAndComments() {
            Map<String, Object> parsed = DotenvEnvironmentPostProcessor.parse(List.of(
                    "# comentario", "", "A=1", "B=\"dos tres\"", "C='cuatro # no comentario'", "D=cinco # comentario",
                    "export E=seis", "invalido", "1X=no"));
            assertThat(parsed).containsEntry("A", "1").containsEntry("B", "dos tres").containsEntry("C", "cuatro # no comentario")
                    .containsEntry("D", "cinco").containsEntry("E", "seis").doesNotContainKey("1X").hasSize(5);
        }
    }
}

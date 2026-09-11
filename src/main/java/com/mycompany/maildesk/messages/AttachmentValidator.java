package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.common.BusinessException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Validación de adjuntos: extensión en lista permitida, tipo determinado por firma binaria (no por
 * lo que declare el navegador) y nombre saneado. Nunca se aceptan ejecutables, scripts ni HTML/SVG.
 */
public final class AttachmentValidator {

    public record Validated(String safeName, String contentType) {}

    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"));

    private AttachmentValidator() {}

    public static String allowedExtensionsLabel() {
        return "pdf, png, jpg, jpeg, gif, webp, docx, xlsx, pptx, zip, txt, csv";
    }

    public static Validated validate(String originalName, byte[] head, long size) {
        String name = sanitizeName(originalName);
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new BusinessException("El archivo debe tener una extensión permitida (" + allowedExtensionsLabel() + ").");
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expected = ALLOWED.get(ext);
        if (expected == null) {
            throw new BusinessException("Tipo de archivo no permitido: ." + ext + ". Permitidos: " + allowedExtensionsLabel() + ".");
        }
        if (size <= 0) {
            throw new BusinessException("El archivo está vacío.");
        }
        if (!matchesSignature(ext, head)) {
            throw new BusinessException("El contenido de \"" + name + "\" no corresponde a un archivo ." + ext + " válido.");
        }
        return new Validated(name, expected);
    }

    static String sanitizeName(String originalName) {
        if (originalName == null) {
            throw new BusinessException("Nombre de archivo no válido.");
        }
        String name = originalName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new BusinessException("Nombre de archivo no válido.");
        }
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot) : "";
            name = name.substring(0, 200 - ext.length()) + ext;
        }
        return name;
    }

    static boolean matchesSignature(String ext, byte[] head) {
        if (head == null) {
            return false;
        }
        return switch (ext) {
            case "pdf" -> startsWith(head, "%PDF".getBytes());
            case "png" -> startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "jpg", "jpeg" -> startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "gif" -> startsWith(head, "GIF87a".getBytes()) || startsWith(head, "GIF89a".getBytes());
            case "webp" -> head.length >= 12 && startsWith(head, "RIFF".getBytes())
                    && Arrays.equals(Arrays.copyOfRange(head, 8, 12), "WEBP".getBytes());
            case "docx", "xlsx", "pptx", "zip" -> startsWith(head, new byte[]{'P', 'K', 0x03, 0x04});
            case "txt", "csv" -> isPlausibleText(head);
            default -> false;
        };
    }

    private static boolean isPlausibleText(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

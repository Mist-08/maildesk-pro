package com.mycompany.maildesk.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Formateo de fechas para las vistas (zona horaria del servidor). */
@Component("fmt")
public class ViewFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-MX"))
            .withZone(ZoneId.systemDefault());

    public String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    public String date(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    public String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}

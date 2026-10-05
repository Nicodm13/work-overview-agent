package dk.school.workoverviewagent.source.graph;

import org.jsoup.Jsoup;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

final class GraphValues {

    private GraphValues() {
    }

    static String string(Map<String, Object> value, String key) {
        var result = value.get(key);
        return result instanceof String text && !text.isBlank() ? text : null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Map<String, Object> value, String key) {
        var result = value.get(key);
        return result instanceof Map<?, ?> ? (Map<String, Object>) result : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> value, String key) {
        var result = value.get(key);
        return result instanceof List<?> ? (List<Map<String, Object>>) result : List.of();
    }

    static Instant instant(Map<String, Object> value, String key) {
        var text = string(value, key);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (java.time.format.DateTimeParseException alsoIgnored) {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            }
        }
    }

    static String htmlText(String html) {
        return html == null ? "" : Jsoup.parse(html).body().text();
    }

    static String emailAddress(Map<String, Object> value) {
        return string(object(value, "emailAddress"), "address");
    }
}

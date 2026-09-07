package dk.school.workoverviewagent.source.mock;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.contract.SourceItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MockSourceDataLoader {

    public List<SourceItem> loadItems(String csvResource, SourceType sourceType) {
        return readCsv(csvResource).stream()
                .map(row -> toSourceItem(row, sourceType))
                .toList();
    }

    private SourceItem toSourceItem(Map<String, String> row, SourceType sourceType) {
        var content = row.get("content");
        if (row.get("document") != null && !row.get("document").isBlank()) {
            content = readDocument(row.get("document"));
        }
        return new SourceItem(
                required(row, "id"),
                sourceType,
                Instant.parse(required(row, "occurredAt")),
                required(row, "title"),
                content,
                required(row, "senderOrOrganizer"),
                participants(row.get("participants")),
                attributes(row));
    }

    private List<Map<String, String>> readCsv(String resource) {
        try (var stream = getClass().getResourceAsStream("/mock-data/" + resource)) {
            if (stream == null) {
                throw new IllegalStateException("Mock data resource not found: " + resource);
            }
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                var lines = reader.lines().toList();
                if (lines.isEmpty()) {
                    return List.of();
                }
                var headerLine = lines.getFirst().equalsIgnoreCase("sep=,")
                        ? lines.get(1)
                        : lines.getFirst();
                var headers = parseCsvLine(headerLine);
                return lines.stream()
                        .skip(lines.getFirst().equalsIgnoreCase("sep=,") ? 2 : 1)
                        .filter(line -> !line.isBlank())
                        .map(line -> row(headers, parseCsvLine(line)))
                        .toList();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read mock data resource: " + resource, exception);
        }
    }

    private String readDocument(String resource) {
        try (var stream = getClass().getResourceAsStream("/mock-data/meeting-notes/" + resource)) {
            if (stream == null) {
                throw new IllegalStateException("Meeting note resource not found: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read meeting note: " + resource, exception);
        }
    }

    private Map<String, String> row(List<String> headers, List<String> values) {
        if (headers.size() != values.size()) {
            throw new IllegalArgumentException("Mock CSV row has a different number of columns than its header");
        }
        var row = new LinkedHashMap<String, String>();
        for (var index = 0; index < headers.size(); index++) {
            row.put(headers.get(index), values.get(index));
        }
        return row;
    }

    private List<String> parseCsvLine(String line) {
        var values = new ArrayList<String>();
        var value = new StringBuilder();
        var quoted = false;
        for (var index = 0; index < line.length(); index++) {
            var character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        values.add(value.toString().trim());
        return values;
    }

    private List<String> participants(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\|"));
    }

    private Map<String, String> attributes(Map<String, String> row) {
        var attributes = new LinkedHashMap<String, String>();
        row.forEach((key, value) -> {
            if (!List.of("id", "occurredAt", "title", "content", "document", "senderOrOrganizer", "participants")
                    .contains(key)
                    && value != null
                    && !value.isBlank()) {
                attributes.put(key, value);
            }
        });
        return attributes;
    }

    private String required(Map<String, String> row, String key) {
        var value = row.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mock data field must not be blank: " + key);
        }
        return value;
    }
}

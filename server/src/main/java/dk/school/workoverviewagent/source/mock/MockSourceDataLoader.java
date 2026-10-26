package dk.school.workoverviewagent.source.mock;

import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.contract.SourceItem;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "mock")
public class MockSourceDataLoader {

    private static final Pattern MEETING_NOTE_FILENAME = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}) - Minutes of Meeting - .+\\.docx$",
        Pattern.CASE_INSENSITIVE);

    public List<Contact> loadContacts() {
        return readCsv("users.csv").stream()
            .map(row -> {
                var userPrincipalName = required(row, "userPrincipalName");
                return new Contact(
                    userPrincipalName,
                    required(row, "displayName"),
                    userPrincipalName,
                    userPrincipalName,
                    row.get("role"));
            })
            .toList();
    }

    public List<SourceItem> loadItems(String csvResource, SourceType sourceType) {
        return readCsv(csvResource).stream()
            .map(row -> toSourceItem(row, sourceType))
            .toList();
    }

    public List<SourceItem> loadMeetingNoteEmailItems() {
        return meetingNoteDocumentNames().stream()
            .map(documentName -> {
                var document = readMeetingNoteDocument(documentName);
                var body = "Hi all,\n\nAttached are the minutes of our meeting, \"" + document.meetingTitle()
                    + "\". Please review the decisions and actions relevant to you and let the group know "
                    + "if anything needs correction.\n\nThanks,\n" + document.organizer();
                return new SourceItem(
                    "meeting-note-email-" + meetingNoteId(documentName),
                    SourceType.OUTLOOK,
                    document.occurredAt(),
                    "Minutes of Meeting - " + document.meetingTitle(),
                    body,
                    document.organizer(),
                    document.participants(),
                    Map.of(
                        "folder", "Inbox",
                        "importance", "normal",
                        "topicId", document.topicId(),
                        "hasAttachments", "true",
                        "attachmentName", documentName));
            })
            .toList();
    }

    public List<SourceItem> loadMeetingNoteItems() {
        return meetingNoteDocumentNames().stream()
            .map(documentName -> {
                var document = readMeetingNoteDocument(documentName);
                return new SourceItem(
                    meetingNoteId(documentName),
                    SourceType.MEETING_NOTES,
                    document.occurredAt(),
                    "Potential meeting minutes: " + documentName,
                    document.text(),
                    document.organizer(),
                    document.participants(),
                    Map.of(
                        "origin", "OUTLOOK",
                        "attachmentName", documentName,
                        "topicId", document.topicId()));
            })
            .toList();
    }

    private List<String> meetingNoteDocumentNames() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var resources = resolver.getResources("classpath:/mock-data/meeting-notes/*.docx");
            return java.util.Arrays.stream(resources)
                .map(resource -> resource.getFilename())
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list mock meeting-note documents", exception);
        }
    }

    private String meetingNoteId(String documentName) {
        Matcher matcher = MEETING_NOTE_FILENAME.matcher(documentName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Meeting-note document must use the YYYY-MM-DD naming pattern: "
                + documentName);
        }
        return "notes-" + matcher.group(1);
    }

    private SourceItem toSourceItem(Map<String, String> row, SourceType sourceType) {
        return new SourceItem(
            required(row, "id"),
            sourceType,
            Instant.parse(required(row, "occurredAt")),
            required(row, "title"),
            row.get("content"),
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

    private MeetingNoteDocument readMeetingNoteDocument(String resource) {
        try (var stream = getClass().getResourceAsStream("/mock-data/meeting-notes/" + resource)) {
            if (stream == null) {
                throw new IllegalStateException("Meeting note resource not found: " + resource);
            }
            if (!resource.toLowerCase().endsWith(".docx")) {
                throw new IllegalArgumentException("Meeting note must be a DOCX file: " + resource);
            }
            String text;
            try (var document = new XWPFDocument(new ByteArrayInputStream(stream.readAllBytes()));
                 var extractor = new XWPFWordExtractor(document)) {
                text = extractor.getText().strip();
            }
            var metadata = new LinkedHashMap<String, String>();
            var noteStarted = false;
            for (var line : text.lines().map(String::strip).toList()) {
                if (line.equals("Meeting notes")) {
                    noteStarted = true;
                    continue;
                }
                if (noteStarted) { continue; }
                var separator = line.indexOf(':');
                if (separator > 0) {
                    metadata.put(line.substring(0, separator), line.substring(separator + 1).strip());
                }
            }
            var attendees = required(metadata, "Participants").split(";");
            return new MeetingNoteDocument(
                required(metadata, "Meeting"),
                Instant.parse(required(metadata, "Date and time")),
                required(metadata, "Organizer"),
                List.of(attendees).stream().map(String::strip).filter(value -> !value.isBlank()).toList(),
                required(metadata, "Topic"),
                text);
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

    private record MeetingNoteDocument(
        String meetingTitle,
        Instant occurredAt,
        String organizer,
        List<String> participants,
        String topicId,
        String text) {
    }
}

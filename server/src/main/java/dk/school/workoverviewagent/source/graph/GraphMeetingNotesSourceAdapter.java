package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphMeetingNotesSourceAdapter implements ISourceAdapter {

    private static final int MAX_DOCX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 32_000;

    private final GraphSourceClient graph;
    private final SourceItemFilter filter;

    GraphMeetingNotesSourceAdapter(GraphSourceClient graph, SourceItemFilter filter) {
        this.graph = graph;
        this.filter = filter;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.MEETING_NOTES;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        var session = graph.open(request);
        var query = new LinkedHashMap<String, String>();
        query.put("$select", "id,subject,receivedDateTime,from,hasAttachments,webLink");
        query.put("$top", "50");
        var dateFilter = dateFilter(request);
        if (dateFilter != null) {
            query.put("$filter", dateFilter);
        }
        var messages = session.list(graph.uri("/me/messages", query));
        var items = new ArrayList<SourceItem>();
        var limitations = new ArrayList<String>();
        if (messages.truncated()) {
            limitations.add("Outlook messages exceeded the 20-page meeting-minutes review limit.");
        }
        for (var message : messages.items()) {
            var messageId = GraphValues.string(message, "id");
            var receivedAt = GraphValues.instant(message, "receivedDateTime");
            if (messageId == null || receivedAt == null || !Boolean.TRUE.equals(message.get("hasAttachments"))
                || request.startsAt() != null && receivedAt.isBefore(request.startsAt())
                || request.endsAt() != null && receivedAt.isAfter(request.endsAt())) {
                continue;
            }
            var attachments = session.list(graph.uriSegments(
                Map.of("$select", "id,name,size,isInline", "$top", "50"),
                "me", "messages", messageId, "attachments"));
            if (attachments.truncated()) {
                limitations.add("Attachments on an Outlook message exceeded the 20-page review limit.");
            }
            for (var attachment : attachments.items()) {
                addDocx(session, message, messageId, receivedAt, attachment, items, limitations);
            }
        }
        return new SourceResult(sourceType(), filter.matching(request, items), limitations);
    }

    private void addDocx(
        GraphSourceClient.Session session,
        Map<String, Object> message,
        String messageId,
        Instant receivedAt,
        Map<String, Object> attachment,
        List<SourceItem> items,
        List<String> limitations) {
        var attachmentId = GraphValues.string(attachment, "id");
        var name = GraphValues.string(attachment, "name");
        var type = GraphValues.string(attachment, "@odata.type");
        if (attachmentId == null || name == null || !name.toLowerCase(Locale.ROOT).endsWith(".docx")
            || Boolean.TRUE.equals(attachment.get("isInline"))
            || type != null && !type.equals("#microsoft.graph.fileAttachment")) {
            return;
        }
        if (attachment.get("size") instanceof Number size && size.longValue() > MAX_DOCX_BYTES) {
            limitations.add("A DOCX email attachment exceeded the 5 MB meeting-minutes limit.");
            return;
        }
        var file = session.getJson(graph.uriSegments(Map.of(),
            "me", "messages", messageId, "attachments", attachmentId));
        var encoded = GraphValues.string(file, "contentBytes");
        if (encoded == null || encoded.length() > (MAX_DOCX_BYTES + 2L) / 3L * 4L) {
            limitations.add("A DOCX email attachment could not be read within the 5 MB limit.");
            return;
        }
        String content;
        try {
            var bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > MAX_DOCX_BYTES) {
                limitations.add("A DOCX email attachment exceeded the 5 MB meeting-minutes limit.");
                return;
            }
            content = extractText(bytes);
        } catch (IOException | RuntimeException exception) {
            limitations.add("A DOCX email attachment could not be read; some meeting minutes may be missing.");
            return;
        }
        if (content.isBlank()) {
            limitations.add("A DOCX email attachment contained no readable text.");
            return;
        }
        if (content.length() > MAX_TEXT_LENGTH) {
            content = content.substring(0, MAX_TEXT_LENGTH);
            limitations.add("A DOCX meeting-minutes attachment was truncated to 32,000 characters.");
        }
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("origin", "OUTLOOK");
        attributes.put("outlookMessageId", messageId);
        attributes.put("attachmentName", name);
        var webLink = GraphValues.string(message, "webLink");
        if (webLink != null) {
            attributes.put("webLink", webLink);
        }
        var sender = GraphValues.object(message, "from");
        var senderAddress = GraphValues.emailAddress(sender);
        var senderName = GraphValues.string(GraphValues.object(sender, "emailAddress"), "name");
        items.add(new SourceItem(
            messageId + ":" + attachmentId,
            sourceType(),
            receivedAt,
            "Potential meeting minutes: " + name,
            content,
            senderAddress == null ? senderName == null ? "Unknown sender" : senderName : senderAddress,
            List.of(),
            attributes));
    }

    private static String extractText(byte[] bytes) throws IOException {
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
             var extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().strip();
        }
    }

    private static String dateFilter(SourceRequest request) {
        var clauses = new ArrayList<String>();
        if (request.startsAt() != null) {
            clauses.add("receivedDateTime ge " + request.startsAt());
        }
        if (request.endsAt() != null) {
            clauses.add("receivedDateTime le " + request.endsAt());
        }
        return clauses.isEmpty() ? null : String.join(" and ", clauses);
    }
}

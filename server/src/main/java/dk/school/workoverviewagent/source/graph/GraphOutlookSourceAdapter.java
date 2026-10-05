package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphOutlookSourceAdapter implements ISourceAdapter {

    private static final String SELECT = "id,subject,bodyPreview,receivedDateTime,sentDateTime,from,toRecipients,webLink";

    private final GraphSourceClient graph;
    private final SourceItemFilter filter;

    GraphOutlookSourceAdapter(GraphSourceClient graph, SourceItemFilter filter) {
        this.graph = graph;
        this.filter = filter;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.OUTLOOK;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        var session = graph.open(request);
        var items = new LinkedHashMap<String, SourceItem>();
        var limitations = new ArrayList<String>();
        loadMessages(session, request, "receivedDateTime", items, limitations);
        loadMessages(session, request, "sentDateTime", items, limitations);
        return new SourceResult(sourceType(), filter.matching(request, List.copyOf(items.values())), limitations);
    }

    private void loadMessages(
        GraphSourceClient.Session session,
        SourceRequest request,
        String timestampField,
        Map<String, SourceItem> items,
        List<String> limitations) {
        var query = new LinkedHashMap<String, String>();
        query.put("$select", SELECT);
        query.put("$top", "50");
        var dateFilter = dateFilter(timestampField, request.startsAt(), request.endsAt());
        if (dateFilter != null) {
            query.put("$filter", dateFilter);
        }
        var result = session.list(graph.uri("/me/messages", query));
        for (var message : result.items()) {
            var id = GraphValues.string(message, "id");
            var occurredAt = GraphValues.instant(message, timestampField);
            if (id == null || occurredAt == null) {
                continue;
            }
            var recipients = GraphValues.objects(message, "toRecipients").stream()
                .map(GraphValues::emailAddress)
                .filter(address -> address != null)
                .toList();
            var attributes = new LinkedHashMap<String, String>();
            var webLink = GraphValues.string(message, "webLink");
            if (webLink != null) {
                attributes.put("webLink", webLink);
            }
            items.putIfAbsent(id, new SourceItem(
                id,
                sourceType(),
                occurredAt,
                valueOr(GraphValues.string(message, "subject"), "(no subject)"),
                valueOr(GraphValues.string(message, "bodyPreview"), ""),
                valueOr(GraphValues.emailAddress(GraphValues.object(message, "from")), "Unknown sender"),
                recipients,
                attributes));
        }
        if (result.truncated()) {
            limitations.add("Outlook " + timestampField + " results exceeded the 20-page review limit.");
        }
    }

    private static String dateFilter(String field, Instant startsAt, Instant endsAt) {
        var clauses = new ArrayList<String>();
        if (startsAt != null) {
            clauses.add(field + " ge " + startsAt);
        }
        if (endsAt != null) {
            clauses.add(field + " le " + endsAt);
        }
        return clauses.isEmpty() ? null : String.join(" and ", clauses);
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphMeetingNotesSourceAdapter implements ISourceAdapter {

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
        query.put("$select", "id,title,lastModifiedDateTime,links");
        query.put("$top", "50");
        if (request.startsAt() != null) {
            query.put("$filter", "lastModifiedDateTime ge " + request.startsAt());
        }
        var pages = session.list(graph.uri("/me/onenote/pages", query));
        var items = new ArrayList<SourceItem>();
        for (var page : pages.items()) {
            var id = GraphValues.string(page, "id");
            var occurredAt = GraphValues.instant(page, "lastModifiedDateTime");
            if (id == null || occurredAt == null
                || request.startsAt() != null && occurredAt.isBefore(request.startsAt())
                || request.endsAt() != null && occurredAt.isAfter(request.endsAt())) {
                continue;
            }
            var html = session.getHtml(graph.uriSegments(Map.of(), "me", "onenote", "pages", id, "content"));
            var attributes = new LinkedHashMap<String, String>();
            var webLink = GraphValues.string(
                GraphValues.object(GraphValues.object(page, "links"), "oneNoteWebUrl"), "href");
            if (webLink != null) {
                attributes.put("webLink", webLink);
            }
            items.add(new SourceItem(
                id, sourceType(), occurredAt,
                GraphValues.string(page, "title") == null ? "Untitled OneNote page" : GraphValues.string(page, "title"),
                GraphValues.htmlText(html), "OneNote", List.of(), attributes));
        }
        var limitations = new ArrayList<String>();
        limitations.add("Meeting notes source covers accessible OneNote pages, not Teams transcripts or all meeting files.");
        if (pages.truncated()) {
            limitations.add("OneNote pages exceeded the 20-page review limit.");
        }
        return new SourceResult(sourceType(), filter.matching(request, items), limitations);
    }
}

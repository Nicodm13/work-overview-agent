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
class GraphCalendarSourceAdapter implements ISourceAdapter {

    private final GraphSourceClient graph;
    private final SourceItemFilter filter;

    GraphCalendarSourceAdapter(GraphSourceClient graph, SourceItemFilter filter) {
        this.graph = graph;
        this.filter = filter;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.CALENDAR;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        if (request.startsAt() == null || request.endsAt() == null) {
            throw new IllegalArgumentException("Calendar reviews require both interval boundaries");
        }
        var session = graph.open(request);
        var query = Map.of(
            "startDateTime", request.startsAt().toString(),
            "endDateTime", request.endsAt().toString(),
            "$select", "id,subject,body,organizer,attendees,start,end,webLink",
            "$top", "50");
        var result = session.list(graph.uri("/me/calendar/calendarView", query));
        var items = new ArrayList<SourceItem>();
        for (var event : result.items()) {
            var id = GraphValues.string(event, "id");
            var startsAt = GraphValues.instant(GraphValues.object(event, "start"), "dateTime");
            if (id == null || startsAt == null) {
                continue;
            }
            var organizer = GraphValues.emailAddress(GraphValues.object(event, "organizer"));
            var participants = GraphValues.objects(event, "attendees").stream()
                .map(GraphValues::emailAddress)
                .filter(address -> address != null)
                .toList();
            var attributes = new LinkedHashMap<String, String>();
            var webLink = GraphValues.string(event, "webLink");
            if (webLink != null) {
                attributes.put("webLink", webLink);
            }
            var endsAt = GraphValues.instant(GraphValues.object(event, "end"), "dateTime");
            if (endsAt != null) {
                attributes.put("endsAt", endsAt.toString());
            }
            items.add(new SourceItem(
                id, sourceType(), startsAt,
                GraphValues.string(event, "subject") == null ? "(untitled meeting)" : GraphValues.string(event, "subject"),
                GraphValues.htmlText(GraphValues.string(GraphValues.object(event, "body"), "content")),
                organizer == null ? "Unknown organizer" : organizer,
                participants,
                attributes));
        }
        var limitations = result.truncated()
            ? List.of("Calendar results exceeded the 20-page review limit.") : List.<String>of();
        return new SourceResult(sourceType(), filter.matching(request, items), limitations);
    }
}

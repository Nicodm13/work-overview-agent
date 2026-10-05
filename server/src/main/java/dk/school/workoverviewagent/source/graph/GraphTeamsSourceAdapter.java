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
class GraphTeamsSourceAdapter implements ISourceAdapter {

    private final GraphSourceClient graph;
    private final SourceItemFilter filter;

    GraphTeamsSourceAdapter(GraphSourceClient graph, SourceItemFilter filter) {
        this.graph = graph;
        this.filter = filter;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.TEAMS;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        var session = graph.open(request);
        var chats = session.list(graph.uri("/me/chats", Map.of("$top", "50")));
        var items = new ArrayList<SourceItem>();
        var limitations = new ArrayList<String>();
        if (chats.truncated()) {
            limitations.add("Teams chats exceeded the 20-page review limit.");
        }
        for (var chat : chats.items()) {
            var chatId = GraphValues.string(chat, "id");
            if (chatId == null) {
                continue;
            }
            var query = new LinkedHashMap<String, String>();
            query.put("$top", "50");
            if (request.startsAt() != null) {
                query.put("$orderby", "lastModifiedDateTime desc");
                query.put("$filter", "lastModifiedDateTime gt " + request.startsAt());
            }
            var messages = session.list(graph.uriSegments(query, "me", "chats", chatId, "messages"));
            if (messages.truncated()) {
                limitations.add("Messages in Teams chat " + chatId + " exceeded the 20-page review limit.");
            }
            for (var message : messages.items()) {
                var messageId = GraphValues.string(message, "id");
                var occurredAt = GraphValues.instant(message, "createdDateTime");
                if (messageId == null || occurredAt == null) {
                    continue;
                }
                var sender = GraphValues.object(GraphValues.object(message, "from"), "user");
                var senderName = GraphValues.string(sender, "displayName");
                if (senderName == null) {
                    senderName = GraphValues.string(sender, "id");
                }
                var attributes = new LinkedHashMap<String, String>();
                attributes.put("chatId", chatId);
                var webUrl = GraphValues.string(message, "webUrl");
                if (webUrl != null) {
                    attributes.put("webLink", webUrl);
                }
                items.add(new SourceItem(
                    chatId + ":" + messageId,
                    sourceType(), occurredAt,
                    GraphValues.string(chat, "topic") == null ? "Teams chat" : GraphValues.string(chat, "topic"),
                    GraphValues.htmlText(GraphValues.string(GraphValues.object(message, "body"), "content")),
                    senderName == null ? "Unknown sender" : senderName,
                    List.of(),
                    attributes));
            }
        }
        limitations.add("Teams source includes chats only; channel posts require separate permissions and an adapter.");
        return new SourceResult(sourceType(), filter.matching(request, items), limitations);
    }
}

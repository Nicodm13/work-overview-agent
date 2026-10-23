package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
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
        loadChannelPosts(session, items, limitations);
        return new SourceResult(sourceType(), filter.matching(request, items), limitations);
    }

    private void loadChannelPosts(
        GraphSourceClient.Session session,
        List<SourceItem> items,
        List<String> limitations) {
        GraphSourceClient.ListResult teams;
        try {
            teams = session.list(graph.uri("/me/joinedTeams", Map.of()));
        } catch (RestClientResponseException exception) {
            if (isForbidden(exception)) {
                addLimitationOnce(limitations,
                    "Teams channel posts were denied by Microsoft Graph. Check delegated Team.ReadBasic.All, "
                        + "Channel.ReadBasic.All, and ChannelMessage.Read.All permissions and consent.");
                return;
            }
            throw exception;
        }
        if (teams.truncated()) {
            addLimitationOnce(limitations, "Teams membership exceeded the 20-page review limit.");
        }

        for (var team : teams.items()) {
            var teamId = GraphValues.string(team, "id");
            if (teamId == null) {
                continue;
            }
            var teamName = valueOr(GraphValues.string(team, "displayName"), "Team");
            GraphSourceClient.ListResult channels;
            try {
                channels = session.list(graph.uriSegments(Map.of(), "teams", teamId, "channels"));
            } catch (RestClientResponseException exception) {
                if (isForbidden(exception)) {
                    addLimitationOnce(limitations,
                        "Teams channel posts were denied by Microsoft Graph. Check delegated "
                            + "Channel.ReadBasic.All and ChannelMessage.Read.All permissions and consent.");
                    continue;
                }
                throw exception;
            }
            if (channels.truncated()) {
                addLimitationOnce(limitations,
                    "Channels in at least one joined team exceeded the 20-page review limit.");
            }

            for (var channel : channels.items()) {
                var channelId = GraphValues.string(channel, "id");
                if (channelId == null) {
                    continue;
                }
                var channelName = valueOr(GraphValues.string(channel, "displayName"), "Channel");
                var query = Map.of("$top", "50", "$expand", "replies");
                GraphSourceClient.ListResult messages;
                try {
                    messages = session.list(graph.uriSegments(
                        query, "teams", teamId, "channels", channelId, "messages"));
                } catch (RestClientResponseException exception) {
                    if (isForbidden(exception)) {
                        addLimitationOnce(limitations,
                            "Teams channel posts were denied by Microsoft Graph. Check delegated "
                                + "ChannelMessage.Read.All permission and consent.");
                        continue;
                    }
                    throw exception;
                }
                if (messages.truncated()) {
                    addLimitationOnce(limitations,
                        "Posts in at least one Teams channel exceeded the 20-page review limit.");
                }
                for (var message : messages.items()) {
                    addChannelMessage(message, teamId, teamName, channelId, channelName, items);
                    for (var reply : GraphValues.objects(message, "replies")) {
                        addChannelMessage(reply, teamId, teamName, channelId, channelName, items);
                    }
                    var repliesNextLink = GraphValues.string(message, "replies@odata.nextLink");
                    if (repliesNextLink != null) {
                        var replies = session.list(URI.create(repliesNextLink));
                        if (replies.truncated()) {
                            addLimitationOnce(limitations,
                                "Replies in at least one Teams thread exceeded the 20-page review limit.");
                        }
                        for (var reply : replies.items()) {
                            addChannelMessage(reply, teamId, teamName, channelId, channelName, items);
                        }
                    }
                }
            }
        }
    }

    private void addChannelMessage(
        Map<String, Object> message,
        String teamId,
        String teamName,
        String channelId,
        String channelName,
        List<SourceItem> items) {
        var messageId = GraphValues.string(message, "id");
        var occurredAt = GraphValues.instant(message, "createdDateTime");
        if (messageId == null || occurredAt == null) {
            return;
        }

        var from = GraphValues.object(message, "from");
        var sender = GraphValues.object(from, "user");
        if (sender.isEmpty()) {
            sender = GraphValues.object(from, "application");
        }
        var senderName = GraphValues.string(sender, "displayName");
        if (senderName == null) {
            senderName = GraphValues.string(sender, "id");
        }
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("teamId", teamId);
        attributes.put("teamName", teamName);
        attributes.put("channelId", channelId);
        attributes.put("channelName", channelName);
        var webLink = GraphValues.string(message, "webUrl");
        if (webLink != null) {
            attributes.put("webLink", webLink);
        }
        items.add(new SourceItem(
            teamId + ":" + channelId + ":" + messageId,
            sourceType(),
            occurredAt,
            teamName + " / " + channelName,
            GraphValues.htmlText(GraphValues.string(GraphValues.object(message, "body"), "content")),
            senderName == null ? "Unknown sender" : senderName,
            List.of(),
            attributes));
    }

    private static boolean isForbidden(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 403;
    }

    private static void addLimitationOnce(List<String> limitations, String limitation) {
        if (!limitations.contains(limitation)) {
            limitations.add(limitation);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

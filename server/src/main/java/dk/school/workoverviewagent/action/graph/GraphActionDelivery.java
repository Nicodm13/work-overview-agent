package dk.school.workoverviewagent.action.graph;

import dk.school.workoverviewagent.action.ActionDeliveryException;
import dk.school.workoverviewagent.action.api.IActionDelivery;
import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.api.IContactDirectory;
import dk.school.workoverviewagent.graph.IGraphAccessTokenProvider;
import dk.school.workoverviewagent.model.ActionDeliveryResult;
import dk.school.workoverviewagent.model.ActionDeliveryStatus;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphActionDelivery implements IActionDelivery {

    private static final URI GRAPH_ROOT = URI.create("https://graph.microsoft.com/v1.0");

    private final IGraphAccessTokenProvider tokenProvider;
    private final IUserProvider userProvider;
    private final IContactDirectory contactDirectory;
    private final RestClient restClient;

    @Autowired
    GraphActionDelivery(
        IGraphAccessTokenProvider tokenProvider,
        IUserProvider userProvider,
        IContactDirectory contactDirectory) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.tokenProvider = tokenProvider;
        this.userProvider = userProvider;
        this.contactDirectory = contactDirectory;
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    GraphActionDelivery(
        IGraphAccessTokenProvider tokenProvider,
        IUserProvider userProvider,
        IContactDirectory contactDirectory,
        RestClient restClient) {
        this.tokenProvider = tokenProvider;
        this.userProvider = userProvider;
        this.contactDirectory = contactDirectory;
        this.restClient = restClient;
    }

    @Override
    public ActionDeliveryResult deliver(ActionDraft draft) {
        try {
            return switch (draft.actionType()) {
                case TEAMS_MESSAGE -> sendTeamsMessage(draft);
                case EMAIL -> sendEmail(draft);
                case MEETING_INVITATION -> createMeeting(draft);
            };
        } catch (RestClientResponseException exception) {
            throw new ActionDeliveryException(
                "Microsoft Graph returned HTTP " + exception.getStatusCode().value(),
                exception.getStatusCode().is5xxServerError(),
                exception);
        } catch (ActionDeliveryException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new ActionDeliveryException(
                "The Microsoft Graph outcome is unknown because the request timed out or the connection failed",
                true,
                exception);
        } catch (RuntimeException exception) {
            throw new ActionDeliveryException("Microsoft Graph action request failed", exception);
        }
    }

    private ActionDeliveryResult sendTeamsMessage(ActionDraft draft) {
        var recipientContacts = resolveTenantContacts(draft.recipients());
        var members = new ArrayList<Map<String, Object>>();
        members.add(conversationMember(currentObjectId()));
        recipientContacts.stream()
            .map(Contact::id)
            .filter(id -> !id.equalsIgnoreCase(currentObjectId()))
            .distinct()
            .map(this::conversationMember)
            .forEach(members::add);
        if (members.size() < 2) {
            throw new ActionDeliveryException("A Teams message requires at least one recipient other than the sender");
        }

        var chatRequest = new LinkedHashMap<String, Object>();
        chatRequest.put("chatType", members.size() == 2 ? "oneOnOne" : "group");
        if (members.size() > 2) {
            chatRequest.put("topic", "Work overview follow-up");
        }
        chatRequest.put("members", members);
        var chat = post(path("chats"), chatRequest);
        var chatId = string(chat, "id");
        if (chatId == null || chatId.isBlank()) {
            throw new ActionDeliveryException("Microsoft Graph did not return a chat ID");
        }

        var messageBody = Map.<String, Object>of(
            "body", Map.of("contentType", "text", "content", draft.body()));
        var message = post(path("chats", chatId, "messages"), messageBody);
        var messageId = string(message, "id");
        return new ActionDeliveryResult(
            ActionDeliveryStatus.ACCEPTED_BY_GRAPH,
            messageId == null ? chatId : chatId + "/" + messageId);
    }

    private ActionDeliveryResult sendEmail(ActionDraft draft) {
        var recipients = draft.recipients().stream()
            .map(this::emailAddress)
            .map(address -> Map.<String, Object>of("emailAddress", Map.of("address", address)))
            .toList();
        var message = Map.<String, Object>of(
            "subject", draft.subject(),
            "body", Map.of("contentType", "Text", "content", draft.body()),
            "toRecipients", recipients);
        post(path("me", "sendMail"), Map.of("message", message));
        return new ActionDeliveryResult(ActionDeliveryStatus.ACCEPTED_BY_GRAPH, null);
    }

    private ActionDeliveryResult createMeeting(ActionDraft draft) {
        var attendees = draft.recipients().stream()
            .map(this::meetingAttendee)
            .toList();
        var body = draft.body() == null || draft.body().isBlank()
            ? agendaHtml(draft.agenda())
            : html(draft.body());
        var event = new LinkedHashMap<String, Object>();
        event.put("subject", draft.meetingTitle());
        event.put("body", Map.of("contentType", "HTML", "content", body));
        event.put("start", graphDateTime(draft.selectedStartsAt()));
        event.put("end", graphDateTime(draft.selectedEndsAt()));
        event.put("attendees", attendees);
        event.put("transactionId", draft.id());
        var response = post(path("me", "events"), event);
        return new ActionDeliveryResult(ActionDeliveryStatus.ACCEPTED_BY_GRAPH, string(response, "id"));
    }

    private List<Contact> resolveTenantContacts(List<String> recipients) {
        var contacts = new LinkedHashMap<String, Contact>();
        for (var recipient : recipients) {
            var contact = resolveContact(recipient);
            contacts.putIfAbsent(contact.id().toLowerCase(Locale.ROOT), contact);
        }
        return List.copyOf(contacts.values());
    }

    private Map<String, Object> meetingAttendee(String recipient) {
        var emailAddress = new LinkedHashMap<String, Object>();
        var address = emailAddress(recipient);
        emailAddress.put("address", address);
        if (!recipient.contains("@")) {
            emailAddress.put("name", resolveContact(recipient).displayName());
        }
        return Map.of("emailAddress", emailAddress, "type", "required");
    }

    private String emailAddress(String recipient) {
        if (recipient.contains("@")) {
            return recipient;
        }
        var contact = resolveContact(recipient);
        if (contact.emailAddress() == null || contact.emailAddress().isBlank()) {
            throw new ActionDeliveryException("The selected recipient has no email address");
        }
        return contact.emailAddress();
    }

    private Contact resolveContact(String recipient) {
        var normalized = recipient.strip().toLowerCase(Locale.ROOT);
        var matches = contactDirectory.search(recipient).stream()
            .filter(contact -> equalsIgnoreCase(contact.displayName(), normalized)
                || equalsIgnoreCase(contact.emailAddress(), normalized)
                || equalsIgnoreCase(contact.userPrincipalName(), normalized))
            .toList();
        if (matches.size() != 1) {
            throw new ActionDeliveryException(matches.isEmpty()
                ? "Could not resolve the selected recipient in the tenant"
                : "The selected recipient matches multiple tenant users");
        }
        return matches.getFirst();
    }

    private Map<String, Object> conversationMember(String objectId) {
        return Map.of(
            "@odata.type", "#microsoft.graph.aadUserConversationMember",
            "roles", List.of("owner"),
            "user@odata.bind", GRAPH_ROOT + "/users('" + objectId.replace("'", "''") + "')");
    }

    private Map<String, Object> graphDateTime(Instant instant) {
        return Map.of(
            "dateTime", LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString(),
            "timeZone", "UTC");
    }

    private String agendaHtml(List<String> agenda) {
        if (agenda.isEmpty()) {
            return "";
        }
        var html = new StringBuilder("<ul>");
        agenda.forEach(item -> html.append("<li>").append(html(item)).append("</li>"));
        return html.append("</ul>").toString();
    }

    private String html(String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br>");
    }

    private String currentObjectId() {
        var userId = userProvider.getUserId();
        var delimiter = userId.indexOf(':');
        if (delimiter < 1 || delimiter == userId.length() - 1) {
            throw new ActionDeliveryException("The signed-in user ID is not a tenant and object ID pair");
        }
        return userId.substring(delimiter + 1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(URI uri, Map<String, Object> requestBody) {
        var response = restClient.post()
            .uri(uri)
            .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .toEntity(Map.class);
        return (Map<String, Object>) response.getBody();
    }

    private URI path(String... segments) {
        return UriComponentsBuilder.fromUri(GRAPH_ROOT)
            .pathSegment(segments)
            .build()
            .encode()
            .toUri();
    }

    private static String string(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.strip().equalsIgnoreCase(right);
    }
}

package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphSourceAdaptersTest {

    private static final URI ROOT = URI.create("https://graph.microsoft.com/v1.0");
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-07T00:00:00Z");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GraphSourceClient graph = new GraphSourceClient(
        () -> "graph-token", () -> "tenant:user", builder.build(), ROOT);
    private final SourceItemFilter filter = new SourceItemFilter();
    private final SourceRequest request = new SourceRequest("tenant:user", START, END, List.of());

    @Test
    void loadsIncomingAndSentOutlookMessagesWithoutDuplicates() {
        expectJson("/me/messages", """
            {"value":[{"id":"mail-1","subject":"Question","bodyPreview":"Please reply",
              "receivedDateTime":"2026-10-02T10:00:00Z",
              "from":{"emailAddress":{"address":"sender@example.com"}},
              "toRecipients":[{"emailAddress":{"address":"user@example.com"}}],
              "webLink":"https://outlook.office.com/mail/id/mail-1"}]}
            """);
        expectJson("/me/messages", """
            {"value":[{"id":"mail-1","subject":"Question","sentDateTime":"2026-10-02T10:00:00Z"},
              {"id":"mail-2","subject":"Re: Question","bodyPreview":"Answered",
              "sentDateTime":"2026-10-03T11:00:00Z",
              "from":{"emailAddress":{"address":"user@example.com"}},
              "toRecipients":[{"emailAddress":{"address":"sender@example.com"}}]}]}
            """);

        var result = new GraphOutlookSourceAdapter(graph, filter).load(request);

        assertThat(result.items()).extracting(item -> item.id()).containsExactly("mail-1", "mail-2");
        assertThat(result.items().getFirst().senderOrOrganizer()).isEqualTo("sender@example.com");
        assertThat(result.items().getFirst().participants()).containsExactly("user@example.com");
        assertThat(result.items().getFirst().attributes()).containsEntry(
            "webLink", "https://outlook.office.com/mail/id/mail-1");
        server.verify();
    }

    @Test
    void loadsCalendarOccurrencesAndConvertsHtmlToText() {
        expectJson("/me/calendar/calendarView", """
            {"value":[{"id":"event-1","subject":"Planning","start":{"dateTime":"2026-10-04T09:00:00.0000000"},
              "end":{"dateTime":"2026-10-04T10:00:00.0000000"},
              "body":{"content":"<p>Discuss <b>milestones</b></p>"},
              "organizer":{"emailAddress":{"address":"lead@example.com"}},
              "attendees":[{"emailAddress":{"address":"user@example.com"}}]}]}
            """);

        var result = new GraphCalendarSourceAdapter(graph, filter).load(request);

        assertThat(result.sourceType()).isEqualTo(SourceType.CALENDAR);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().content()).isEqualTo("Discuss milestones");
        assertThat(result.items().getFirst().participants()).containsExactly("user@example.com");
        assertThat(result.items().getFirst().attributes()).containsEntry("endsAt", "2026-10-04T10:00:00Z");
        server.verify();
    }

    @Test
    void loadsTeamsChatMessagesOnlyWithinRequestedInterval() {
        expectJson("/me/chats", """
            {"value":[{"id":"chat-a","topic":"Project chat"}]}
            """);
        expectJson("/me/chats/chat-a/messages", """
            {"value":[
              {"id":"message-1","createdDateTime":"2026-10-03T12:00:00Z",
               "from":{"user":{"displayName":"Maja"}},"body":{"content":"<div>Please check this</div>"}},
              {"id":"old-message","createdDateTime":"2026-09-30T12:00:00Z",
               "body":{"content":"Older message"}}]}
            """);

        var result = new GraphTeamsSourceAdapter(graph, filter).load(request);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("chat-a:message-1");
        assertThat(result.items().getFirst().content()).isEqualTo("Please check this");
        assertThat(result.items().getFirst().senderOrOrganizer()).isEqualTo("Maja");
        assertThat(result.limitations()).anyMatch(text -> text.contains("chats only"));
        server.verify();
    }

    @Test
    void loadsMinutesFromDocxMailAttachmentsWithinInterval() throws IOException {
        expectJson("/me/messages", """
            {"value":[
              {"id":"mail-a","subject":"Minutes of Meeting","receivedDateTime":"2026-10-03T14:00:00Z",
               "hasAttachments":true,"from":{"emailAddress":{"name":"Cortex Minutes of Meeting","address":"cortex@example.com"}},
               "webLink":"https://outlook.office.com/mail/mail-a"},
              {"id":"mail-old","subject":"Old minutes","receivedDateTime":"2026-09-29T14:00:00Z",
               "hasAttachments":true}]}
            """);
        expectJson("/me/messages/mail-a/attachments", """
            {"value":[{"id":"attachment-a","name":"Minutes.docx","size":1000,
              "@odata.type":"#microsoft.graph.fileAttachment"},
              {"id":"attachment-pdf","name":"Slides.pdf","size":1000}]}
            """);
        expectJson("/me/messages/mail-a/attachments/attachment-a", """
            {"@odata.type":"#microsoft.graph.fileAttachment","contentBytes":"%s"}
            """.formatted(docx("Decision: ship prototype")));

        var result = new GraphMeetingNotesSourceAdapter(graph, filter).load(request);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().content()).isEqualTo("Decision: ship prototype");
        assertThat(result.items().getFirst().attributes()).containsEntry("webLink", "https://outlook.office.com/mail/mail-a");
        assertThat(result.items().getFirst().attributes()).containsEntry("attachmentName", "Minutes.docx");
        assertThat(result.items().getFirst().senderOrOrganizer()).isEqualTo("cortex@example.com");
        assertThat(result.limitations()).isEmpty();
        server.verify();
    }

    private String docx(String content) throws IOException {
        try (var document = new XWPFDocument(); var bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(content);
            document.write(bytes);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    @Test
    void rejectsSourceRequestsForAnotherUserBeforeAcquiringAToken() {
        var tokenCalls = new AtomicInteger();
        var client = new GraphSourceClient(() -> {
            tokenCalls.incrementAndGet();
            return "graph-token";
        }, () -> "tenant:user", builder.build(), ROOT);

        assertThatThrownBy(() -> client.open(new SourceRequest("tenant:other", START, END, List.of())))
            .isInstanceOf(SecurityException.class);
        assertThat(tokenCalls).hasValue(0);
    }

    @Test
    void followsGraphPagingButRejectsAnotherOrigin() {
        expectJson("/me/chats", """
            {"value":[{"id":"chat-a"}],
             "@odata.nextLink":"https://graph.microsoft.com/v1.0/me/chats?$skiptoken=next"}
            """);
        expectJson("/me/chats", """
            {"value":[{"id":"chat-b"}]}
            """);
        var result = graph.open(request).list(graph.uri("/me/chats", Map.of("$top", "50")));
        assertThat(result.items()).hasSize(2);
        assertThat(result.truncated()).isFalse();
        server.verify();

        var secondBuilder = RestClient.builder();
        var secondServer = MockRestServiceServer.bindTo(secondBuilder).build();
        var secondClient = new GraphSourceClient(() -> "graph-token", () -> "tenant:user", secondBuilder.build(), ROOT);
        secondServer.expect(requestTo(startsWith(ROOT + "/me/chats")))
            .andRespond(withSuccess("""
                {"value":[],"@odata.nextLink":"https://evil.example/collect"}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> secondClient.open(request).list(secondClient.uri("/me/chats", Map.of())))
            .isInstanceOf(IllegalArgumentException.class);
        secondServer.verify();
    }

    private void expectJson(String path, String body) {
        server.expect(requestTo(startsWith(ROOT + path)))
            .andExpect(header("Authorization", "Bearer graph-token"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}

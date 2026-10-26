package dk.school.workoverviewagent.action.graph;

import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.api.IContactDirectory;
import dk.school.workoverviewagent.graph.IGraphAccessTokenProvider;
import dk.school.workoverviewagent.model.ActionDeliveryStatus;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.user.IUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphActionDeliveryTest {

    private static final String ROOT = "https://graph.microsoft.com/v1.0";
    private static final Contact RECIPIENT = new Contact(
        "recipient-id", "Taylor Ng", "taylor@example.test", "taylor@example.test", "Engineer");

    @Test
    void createsChatAndSendsApprovedMessage() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var contactDirectory = contactDirectory();
        when(contactDirectory.search("Taylor Ng")).thenReturn(List.of(RECIPIENT));
        var delivery = delivery(builder, contactDirectory);
        server.expect(requestTo(ROOT + "/chats"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer graph-token"))
            .andExpect(content().json("""
                {
                  "chatType":"oneOnOne",
                  "members":[
                    {"@odata.type":"#microsoft.graph.aadUserConversationMember","roles":["owner"],"user@odata.bind":"https://graph.microsoft.com/v1.0/users('sender-id')"},
                    {"@odata.type":"#microsoft.graph.aadUserConversationMember","roles":["owner"],"user@odata.bind":"https://graph.microsoft.com/v1.0/users('recipient-id')"}
                  ]
                }
                """))
            .andRespond(withSuccess("{\"id\":\"chat-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROOT + "/chats/chat-1/messages"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().json("""
                {"body":{"contentType":"text","content":"The release gate is ready."}}
                """))
            .andRespond(withSuccess("{\"id\":\"message-1\"}", MediaType.APPLICATION_JSON));

        var result = delivery.deliver(draft(ActionType.TEAMS_MESSAGE));

        assertThat(result.status()).isEqualTo(ActionDeliveryStatus.ACCEPTED_BY_GRAPH);
        assertThat(result.externalReference()).isEqualTo("chat-1/message-1");
        server.verify();
    }

    @Test
    void sendsEmailThroughSignedInMailbox() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var delivery = delivery(builder, contactDirectory());
        server.expect(requestTo(ROOT + "/me/sendMail"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer graph-token"))
            .andExpect(content().json("""
                {
                  "message": {
                    "subject":"Release gate status",
                    "body":{"contentType":"Text","content":"The release gate is ready."},
                    "toRecipients":[{"emailAddress":{"address":"taylor@example.test"}}]
                  }
                }
                """))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

        var result = delivery.deliver(draft(ActionType.EMAIL));

        assertThat(result.status()).isEqualTo(ActionDeliveryStatus.ACCEPTED_BY_GRAPH);
        assertThat(result.externalReference()).isNull();
        server.verify();
    }

    @Test
    void createsCalendarEventWithAttendeesAndIdempotencyKey() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var delivery = delivery(builder, contactDirectory());
        server.expect(requestTo(ROOT + "/me/events"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().json("""
                {
                  "subject":"Release gate review",
                  "start":{"dateTime":"2026-11-02T10:00","timeZone":"UTC"},
                  "end":{"dateTime":"2026-11-02T10:30","timeZone":"UTC"},
                  "attendees":[{"emailAddress":{"address":"taylor@example.test"},"type":"required"}],
                  "transactionId":"draft-1"
                }
                """))
            .andRespond(withSuccess("{\"id\":\"event-1\"}", MediaType.APPLICATION_JSON));

        var result = delivery.deliver(draft(ActionType.MEETING_INVITATION));

        assertThat(result.status()).isEqualTo(ActionDeliveryStatus.ACCEPTED_BY_GRAPH);
        assertThat(result.externalReference()).isEqualTo("event-1");
        server.verify();
    }

    @Test
    void reportsGraphFailureWithoutLeakingResponseBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var delivery = delivery(builder, contactDirectory());
        server.expect(requestTo(ROOT + "/me/sendMail"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"Forbidden\",\"message\":\"sensitive response text\"}}"));

        assertThatThrownBy(() -> delivery.deliver(draft(ActionType.EMAIL)))
            .isInstanceOf(dk.school.workoverviewagent.action.ActionDeliveryException.class)
            .hasMessage("Microsoft Graph returned HTTP 403")
            .hasMessageNotContaining("sensitive response text");
        server.verify();
    }

    @Test
    void marksServerErrorsAsUnknownDeliveryOutcome() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var delivery = delivery(builder, contactDirectory());
        server.expect(requestTo(ROOT + "/me/sendMail"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> delivery.deliver(draft(ActionType.EMAIL)))
            .isInstanceOfSatisfying(dk.school.workoverviewagent.action.ActionDeliveryException.class,
                exception -> {
                    assertThat(exception.outcomeUnknown()).isTrue();
                    assertThat(exception.getMessage()).isEqualTo("Microsoft Graph returned HTTP 503");
                });
        server.verify();
    }

    private GraphActionDelivery delivery(RestClient.Builder builder, IContactDirectory contacts) {
        IGraphAccessTokenProvider tokenProvider = () -> "graph-token";
        IUserProvider userProvider = () -> "tenant-id:sender-id";
        return new GraphActionDelivery(tokenProvider, userProvider, contacts, builder.build());
    }

    private IContactDirectory contactDirectory() {
        return mock(IContactDirectory.class);
    }

    private ActionDraft draft(ActionType type) {
        return new ActionDraft(
            "draft-1",
            "tenant-id:sender-id",
            type,
            "follow-up-1",
            List.of(type == ActionType.EMAIL || type == ActionType.MEETING_INVITATION
                ? "taylor@example.test"
                : "Taylor Ng"),
            type == ActionType.EMAIL ? "Release gate status" : null,
            type == ActionType.MEETING_INVITATION ? null : "The release gate is ready.",
            type == ActionType.MEETING_INVITATION ? "Release gate review" : null,
            type == ActionType.MEETING_INVITATION ? Instant.parse("2026-11-02T10:00:00Z") : null,
            type == ActionType.MEETING_INVITATION ? Instant.parse("2026-11-02T10:30:00Z") : null,
            List.of("Confirm evidence"),
            "Test context");
    }
}

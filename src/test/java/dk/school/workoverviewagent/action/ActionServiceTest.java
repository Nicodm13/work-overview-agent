package dk.school.workoverviewagent.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.model.ActionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionServiceTest {

    private final ActionService actionService = new ActionService();

    @Test
    void shouldCreateExplicitTeamsDraftWithoutInterpretingSourceText() {
        var response = actionService.createDraft(new CreateActionDraftRequest(
                ActionType.TEAMS_MESSAGE,
                "evidence-1",
                List.of("Maja Jensen"),
                null,
                "Could you share a status update?",
                null,
                null,
                null,
                List.of(),
                "Prepared from evidence-1 by the user or MCP client."));

        assertThat(response.draft().actionType()).isEqualTo(ActionType.TEAMS_MESSAGE);
        assertThat(response.draft().recipients()).containsExactly("Maja Jensen");
        assertThat(response.draft().body()).isEqualTo("Could you share a status update?");
        assertThat(response.draft().id()).isNotBlank();
    }

    @Test
    void shouldCreateMeetingDraftWithSelectedTimeAndAgenda() {
        var response = actionService.createDraft(new CreateActionDraftRequest(
                ActionType.MEETING_INVITATION,
                "evidence-2",
                List.of("Maja Jensen", "Jonas Holm"),
                null,
                null,
                "Clarify integration approach",
                Instant.parse("2026-09-08T09:00:00Z"),
                Instant.parse("2026-09-08T09:30:00Z"),
                List.of("Compare the two options", "Agree on next step"),
                "Explicit meeting context supplied by the MCP client."));

        assertThat(response.draft().meetingTitle()).isEqualTo("Clarify integration approach");
        assertThat(response.draft().selectedStartsAt()).isEqualTo(Instant.parse("2026-09-08T09:00:00Z"));
        assertThat(response.draft().agenda()).containsExactly("Compare the two options", "Agree on next step");
    }

    @Test
    void shouldRequireFinalApprovalBeforeApprovedActionIsExecutedAndAuditIt() {
        var draft = actionService.createDraft(new CreateActionDraftRequest(
                ActionType.EMAIL,
                "evidence-3",
                List.of("maja@example.com"),
                "Status update",
                "The agreed update is ready.",
                null,
                null,
                null,
                List.of(),
                "Explicit context."));

        var execution = new ExecuteApprovedActionRequest(
                draft.draft().id(),
                true,
                "user-approved-content-1",
                Instant.parse("2026-09-07T10:00:00Z"));

        assertThatThrownBy(() -> actionService.executeApprovedAction(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("draft must be approved before execution");

        actionService.approveDraft(new ApproveActionRequest(
                draft.draft().id(),
                true,
                "user-approved-content-1",
                Instant.parse("2026-09-07T09:59:00Z")));

        var response = actionService.executeApprovedAction(execution);

        assertThat(response.auditLogEntry().evidenceId()).isEqualTo("evidence-3");
        assertThat(response.auditLogEntry().actionType()).isEqualTo("EMAIL");
        assertThat(actionService.auditLog()).containsExactly(response.auditLogEntry());
    }

    @Test
    void shouldRejectExecutionWhenApprovedContentHasChanged() {
        var draft = actionService.createDraft(new CreateActionDraftRequest(
                ActionType.TEAMS_MESSAGE,
                "evidence-5",
                List.of("Maja Jensen"),
                null,
                "Original draft",
                null,
                null,
                null,
                List.of(),
                "Explicit context."));

        actionService.approveDraft(new ApproveActionRequest(
                draft.draft().id(),
                true,
                "approved-content",
                Instant.parse("2026-09-07T10:00:00Z")));

        assertThatThrownBy(() -> actionService.executeApprovedAction(new ExecuteApprovedActionRequest(
                draft.draft().id(),
                true,
                "changed-content",
                Instant.parse("2026-09-07T10:01:00Z"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("approved content does not match the approved draft");
    }

    @Test
    void shouldRejectExecutionWithoutExplicitFinalApproval() {
        var draft = actionService.createDraft(new CreateActionDraftRequest(
                ActionType.EMAIL,
                "evidence-4",
                List.of("maja@example.com"),
                "Status update",
                "Draft content",
                null,
                null,
                null,
                List.of(),
                "Explicit context."));

        assertThatThrownBy(() -> actionService.approveDraft(new ApproveActionRequest(
                draft.draft().id(),
                false,
                "user-approved-content-2",
                Instant.parse("2026-09-07T10:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finalApproval must be true");
    }
}

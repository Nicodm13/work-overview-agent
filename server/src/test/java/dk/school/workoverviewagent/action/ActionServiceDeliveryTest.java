package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.action.repository.IActionRepository;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.model.ActionDeliveryResult;
import dk.school.workoverviewagent.model.ActionDeliveryStatus;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionState;
import dk.school.workoverviewagent.model.ActionStatus;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionServiceDeliveryTest {

    private static final String OWNER = "tenant-id:user-id";

    @Test
    void recordsGraphDeliveryFailureAndDoesNotReportItAsSent() {
        var repository = mock(IActionRepository.class);
        var delivery = mock(dk.school.workoverviewagent.action.api.IActionDelivery.class);
        var draft = draft("Approved content");
        var reference = ActionApprovalReference.forDraft(draft);
        when(repository.findDraftById(OWNER, draft.id())).thenReturn(Optional.of(draft));
        when(repository.findActionState(OWNER, draft.id())).thenReturn(Optional.of(
            new ActionState(OWNER, draft.id(), ActionStatus.APPROVED, reference, Instant.now())));
        when(repository.claimApprovedDraft(eq(OWNER), eq(draft.id()), eq(reference), any(Instant.class))).thenReturn(true);
        when(delivery.deliver(draft)).thenThrow(new ActionDeliveryException("Microsoft Graph rejected the action with HTTP 403"));
        when(repository.failExecution(org.mockito.ArgumentMatchers.eq(OWNER),
            org.mockito.ArgumentMatchers.eq(draft.id()), any(AuditLogEntry.class))).thenReturn(true);

        var response = service(repository, delivery).executeApprovedAction(
            new ExecuteApprovedActionRequest(OWNER, draft.id(), reference));

        assertThat(response.auditLogEntry().deliveryStatus()).isEqualTo("FAILED");
        assertThat(response.auditLogEntry().externalReference()).isNull();
        assertThat(response.auditLogEntry().failureDetail()).contains("HTTP 403");
        verify(repository, never()).completeExecution(org.mockito.ArgumentMatchers.eq(OWNER),
            org.mockito.ArgumentMatchers.eq(draft.id()), any(AuditLogEntry.class));
    }

    @Test
    void recordsGraphAcceptedOutcomeAndExternalReference() {
        var repository = mock(IActionRepository.class);
        var delivery = mock(dk.school.workoverviewagent.action.api.IActionDelivery.class);
        var draft = draft("Approved content");
        var reference = ActionApprovalReference.forDraft(draft);
        when(repository.findDraftById(OWNER, draft.id())).thenReturn(Optional.of(draft));
        when(repository.findActionState(OWNER, draft.id())).thenReturn(Optional.of(
            new ActionState(OWNER, draft.id(), ActionStatus.APPROVED, reference, Instant.now())));
        when(repository.claimApprovedDraft(eq(OWNER), eq(draft.id()), eq(reference), any(Instant.class))).thenReturn(true);
        when(delivery.deliver(draft)).thenReturn(new ActionDeliveryResult(ActionDeliveryStatus.ACCEPTED_BY_GRAPH, "message-42"));
        when(repository.completeExecution(org.mockito.ArgumentMatchers.eq(OWNER),
            org.mockito.ArgumentMatchers.eq(draft.id()), any(AuditLogEntry.class))).thenReturn(true);

        var response = service(repository, delivery).executeApprovedAction(
            new ExecuteApprovedActionRequest(OWNER, draft.id(), reference));

        assertThat(response.auditLogEntry().deliveryStatus()).isEqualTo("ACCEPTED_BY_GRAPH");
        assertThat(response.auditLogEntry().externalReference()).isEqualTo("message-42");
        assertThat(response.auditLogEntry().failureDetail()).isNull();
    }

    @Test
    void recordsUnknownNetworkOutcomeWithoutCallingItFailedOrSent() {
        var repository = mock(IActionRepository.class);
        var delivery = mock(dk.school.workoverviewagent.action.api.IActionDelivery.class);
        var draft = draft("Approved content");
        var reference = ActionApprovalReference.forDraft(draft);
        when(repository.findDraftById(OWNER, draft.id())).thenReturn(Optional.of(draft));
        when(repository.findActionState(OWNER, draft.id())).thenReturn(Optional.of(
            new ActionState(OWNER, draft.id(), ActionStatus.APPROVED, reference, Instant.now())));
        when(repository.claimApprovedDraft(eq(OWNER), eq(draft.id()), eq(reference), any(Instant.class))).thenReturn(true);
        when(delivery.deliver(draft)).thenThrow(new ActionDeliveryException("Graph outcome unknown", true, null));
        when(repository.failExecution(eq(OWNER), eq(draft.id()), any(AuditLogEntry.class))).thenReturn(true);

        var response = service(repository, delivery).executeApprovedAction(
            new ExecuteApprovedActionRequest(OWNER, draft.id(), reference));

        assertThat(response.auditLogEntry().deliveryStatus()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(response.auditLogEntry().externalReference()).isNull();
    }

    @Test
    void rejectsDraftChangesAfterApprovalBeforeCallingGraph() {
        var repository = mock(IActionRepository.class);
        var delivery = mock(dk.school.workoverviewagent.action.api.IActionDelivery.class);
        var approvedDraft = draft("Original approved content");
        var changedDraft = draft("Changed after approval");
        var reference = ActionApprovalReference.forDraft(approvedDraft);
        when(repository.findDraftById(OWNER, approvedDraft.id())).thenReturn(Optional.of(changedDraft));
        when(repository.findActionState(OWNER, approvedDraft.id())).thenReturn(Optional.of(
            new ActionState(OWNER, approvedDraft.id(), ActionStatus.APPROVED, reference, Instant.now())));

        assertThatThrownBy(() -> service(repository, delivery).executeApprovedAction(
            new ExecuteApprovedActionRequest(OWNER, approvedDraft.id(), reference)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("draft must be approved for its exact current content before execution");
        verify(delivery, never()).deliver(any());
        verify(repository, never()).claimApprovedDraft(any(), any(), any(), any());
    }

    private ActionService service(IActionRepository repository, dk.school.workoverviewagent.action.api.IActionDelivery delivery) {
        return new ActionService(mock(IFollowUpService.class), repository, delivery);
    }

    private ActionDraft draft(String body) {
        return new ActionDraft(
            "draft-id", OWNER, ActionType.TEAMS_MESSAGE, "follow-up-id", List.of("Taylor Ng"),
            null, body, null, null, null, List.of(), "Action context");
    }
}

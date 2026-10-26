package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.api.IActionDelivery;
import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.*;
import dk.school.workoverviewagent.action.repository.IActionRepository;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.model.ActionDeliveryResult;
import dk.school.workoverviewagent.model.ActionDeliveryStatus;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionState;
import dk.school.workoverviewagent.model.ActionStatus;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
class ActionService implements IActionService {

    private final IFollowUpService followUpService;
    private final IActionRepository actionRepository;
    private final IActionDelivery actionDelivery;

    ActionService(
        IFollowUpService followUpService,
        IActionRepository actionRepository,
        IActionDelivery actionDelivery) {
        this.followUpService = followUpService;
        this.actionRepository = actionRepository;
        this.actionDelivery = actionDelivery;
    }

    @Override
    @Transactional
    public CreateActionDraftResponse createDraft(CreateActionDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateDraftRequest(request);
        followUpService.getFollowUpItem(request.ownerId(), request.followUpItemId());

        var draft = new ActionDraft(
            UUID.randomUUID().toString(),
            request.ownerId(),
            request.actionType(),
            request.followUpItemId(),
            request.recipients(),
            request.subject(),
            request.body(),
            request.meetingTitle(),
            request.selectedStartsAt(),
            request.selectedEndsAt(),
            request.agenda(),
            request.editableContext());
        actionRepository.saveDraft(draft.ownerId(), draft);
        actionRepository.createActionState(new ActionState(
            draft.ownerId(),
            draft.id(),
            ActionStatus.DRAFT,
            null,
            Instant.now()));
        return new CreateActionDraftResponse(draft);
    }

    @Override
    @Transactional
    public UpdateActionDraftResponse updateDraft(UpdateActionDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var existingDraft = draftFor(request.ownerId(), request.draftId());
        var updatedDraft = new ActionDraft(
            existingDraft.id(),
            existingDraft.ownerId(),
            existingDraft.actionType(),
            existingDraft.followUpItemId(),
            request.recipients(),
            request.subject(),
            request.body(),
            request.meetingTitle(),
            request.selectedStartsAt(),
            request.selectedEndsAt(),
            request.agenda(),
            request.editableContext());
        validateDraftDetails(updatedDraft);
        if (!actionRepository.updateDraft(request.ownerId(), updatedDraft)) {
            throw new IllegalStateException("only unapproved drafts can be edited");
        }
        return new UpdateActionDraftResponse(updatedDraft);
    }

    @Override
    @Transactional
    public ApproveActionResponse approveDraft(ApproveActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var draft = draftFor(request.ownerId(), request.draftId());
        requireFinalApproval(request.finalApproval());
        var approvedAt = request.approvedAt() == null ? Instant.now() : request.approvedAt();
        var approvalReference = ActionApprovalReference.forDraft(draft);
        var approved = actionRepository.approveDraft(new ActionState(
            request.ownerId(),
            draft.id(),
            ActionStatus.APPROVED,
            approvalReference,
            approvedAt));
        if (!approved) {
            throw new IllegalStateException("draft can only be approved once");
        }
        return new ApproveActionResponse(draft, true, approvedAt, approvalReference);
    }

    @Override
    public ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var draft = draftFor(request.ownerId(), request.draftId());
        var approvalReference = ActionApprovalReference.forDraft(draft);
        var state = actionRepository.findActionState(request.ownerId(), draft.id())
            .orElseThrow(() -> new IllegalStateException("draft has no approval state"));
        if (state.status() != ActionStatus.APPROVED
            || !approvalReference.equals(state.approvedContentReference())
            || !approvalReference.equals(request.approvalReference())) {
            throw new IllegalStateException("draft must be approved for its exact current content before execution");
        }
        var claimedAt = Instant.now();
        var claimed = actionRepository.claimApprovedDraft(
            request.ownerId(),
            draft.id(),
            approvalReference,
            claimedAt);
        if (!claimed) {
            throw new IllegalStateException("approved action is already executing or has already been handled");
        }

        var executedAt = Instant.now();
        ActionDeliveryResult delivery;
        try {
            delivery = actionDelivery.deliver(draft);
        } catch (RuntimeException exception) {
            var failureStatus = exception instanceof ActionDeliveryException deliveryException
                && deliveryException.outcomeUnknown()
                ? ActionDeliveryStatus.OUTCOME_UNKNOWN
                : ActionDeliveryStatus.FAILED;
            var auditEntry = new AuditLogEntry(
                UUID.randomUUID().toString(),
                request.ownerId(),
                draft.followUpItemId(),
                draft.actionType().name(),
                executedAt,
                "APPROVED",
                approvalReference,
                failureStatus.name(),
                null,
                exception instanceof ActionDeliveryException
                    ? exception.getMessage()
                    : "Action delivery failed");
            if (!actionRepository.failExecution(request.ownerId(), draft.id(), auditEntry)) {
                throw new IllegalStateException("action delivery failed and its failure state could not be recorded", exception);
            }
            return new ExecuteApprovedActionResponse(auditEntry);
        }

        var auditEntry = new AuditLogEntry(
            UUID.randomUUID().toString(),
            request.ownerId(),
            draft.followUpItemId(),
            draft.actionType().name(),
            executedAt,
            "APPROVED",
            approvalReference,
            delivery.status().name(),
            delivery.externalReference(),
            null);
        if (!actionRepository.completeExecution(request.ownerId(), draft.id(), auditEntry)) {
            throw new IllegalStateException("action delivery completed but its execution state could not be recorded");
        }
        return new ExecuteApprovedActionResponse(auditEntry);
    }

    @Override
    public ActionDraft getActionDraft(String ownerId, String draftId) {
        validateOwnerId(ownerId);
        return draftFor(ownerId, draftId);
    }

    @Override
    public List<ActionDraft> listActiveActionDrafts(String ownerId) {
        validateOwnerId(ownerId);
        return actionRepository.findActiveDrafts(ownerId);
    }

    @Override
    @Transactional
    public void deleteDraft(String ownerId, String draftId) {
        validateOwnerId(ownerId);
        var state = actionRepository.findActionState(ownerId, draftId)
            .orElseThrow(() -> new IllegalArgumentException("draft not found: " + draftId));
        if (state.status() != ActionStatus.DRAFT) {
            throw new IllegalStateException("only unapproved drafts can be deleted");
        }
        if (!actionRepository.deleteDraft(ownerId, draftId)) {
            throw new IllegalArgumentException("draft not found: " + draftId);
        }
    }

    @Override
    public List<AuditLogEntry> auditLog(String ownerId) {
        validateOwnerId(ownerId);
        return actionRepository.findAuditEntries(ownerId);
    }

    private ActionDraft draftFor(String ownerId, String draftId) {
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
        }
        return actionRepository.findDraftById(ownerId, draftId)
            .orElseThrow(() -> new IllegalArgumentException("draft not found: " + draftId));
    }

    private void validateDraftRequest(CreateActionDraftRequest request) {
        validateOwnerId(request.ownerId());
        Objects.requireNonNull(request.actionType(), "actionType must not be null");
        if (request.followUpItemId() == null || request.followUpItemId().isBlank()) {
            throw new IllegalArgumentException("followUpItemId must not be blank");
        }
        validateDraftDetails(new ActionDraft(
            "validation",
            request.ownerId(),
            request.actionType(),
            request.followUpItemId(),
            request.recipients(),
            request.subject(),
            request.body(),
            request.meetingTitle(),
            request.selectedStartsAt(),
            request.selectedEndsAt(),
            request.agenda(),
            request.editableContext()));
    }

    private void validateDraftDetails(ActionDraft draft) {
        if (draft.recipients().isEmpty() || draft.recipients().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("at least one non-blank recipient is required");
        }
        switch (draft.actionType()) {
            case TEAMS_MESSAGE -> requireText(draft.body(), "body");
            case EMAIL -> {
                requireText(draft.subject(), "subject");
                requireText(draft.body(), "body");
            }
            case MEETING_INVITATION -> {
                requireText(draft.meetingTitle(), "meetingTitle");
                if (draft.selectedStartsAt() == null || draft.selectedEndsAt() == null) {
                    throw new IllegalArgumentException("meeting start and end are required");
                }
                validateDraftTimes(draft.actionType(), draft.selectedStartsAt(), draft.selectedEndsAt());
            }
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private void validateDraftTimes(
        ActionType actionType,
        Instant selectedStartsAt,
        Instant selectedEndsAt) {
        if (actionType == ActionType.MEETING_INVITATION
            && selectedStartsAt != null
            && selectedEndsAt != null
            && selectedEndsAt.isBefore(selectedStartsAt)) {
            throw new IllegalArgumentException("selectedEndsAt must not be before selectedStartsAt");
        }
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    private void requireFinalApproval(boolean finalApproval) {
        if (!finalApproval) {
            throw new IllegalArgumentException("finalApproval must be true");
        }
    }
}

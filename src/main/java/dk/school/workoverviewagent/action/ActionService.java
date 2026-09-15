package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.*;
import dk.school.workoverviewagent.action.repository.IActionRepository;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionState;
import dk.school.workoverviewagent.model.ActionStatus;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ActionService implements IActionService {

    private final IFollowUpService followUpService;
    private final IActionRepository actionRepository;

    ActionService(
        IFollowUpService followUpService,
        IActionRepository actionRepository) {
        this.followUpService = followUpService;
        this.actionRepository = actionRepository;
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
    public ApproveActionResponse approveDraft(ApproveActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var draft = draftFor(request.ownerId(), request.draftId());
        requireFinalApproval(request.finalApproval(), request.approvedContentReference());
        var approvedAt = request.approvedAt() == null ? Instant.now() : request.approvedAt();
        var approved = actionRepository.approveDraft(new ActionState(
            request.ownerId(),
            draft.id(),
            ActionStatus.APPROVED,
            request.approvedContentReference(),
            approvedAt));
        if (!approved) {
            throw new IllegalStateException("draft can only be approved once");
        }
        return new ApproveActionResponse(draft, true, approvedAt);
    }

    @Override
    @Transactional
    public ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var draft = draftFor(request.ownerId(), request.draftId());
        requireFinalApproval(request.finalApproval(), request.approvedContentReference());
        var executedAt = request.executedAt() == null ? Instant.now() : request.executedAt();
        var executed = actionRepository.transitionApprovedDraftToExecuted(
            request.ownerId(),
            draft.id(),
            request.approvedContentReference(),
            executedAt);
        if (!executed) {
            throw new IllegalStateException("draft must be approved with the supplied content before execution");
        }
        var auditEntry = new AuditLogEntry(
            UUID.randomUUID().toString(),
            request.ownerId(),
            draft.followUpItemId(),
            draft.actionType().name(),
            executedAt,
            "APPROVED",
            request.approvedContentReference());
        actionRepository.appendAuditEntry(request.ownerId(), auditEntry);
        return new ExecuteApprovedActionResponse(auditEntry);
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
        if (request.actionType() == ActionType.MEETING_INVITATION
            && request.selectedStartsAt() != null
            && request.selectedEndsAt() != null
            && request.selectedEndsAt().isBefore(request.selectedStartsAt())) {
            throw new IllegalArgumentException("selectedEndsAt must not be before selectedStartsAt");
        }
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    private void requireFinalApproval(boolean finalApproval, String approvedContentReference) {
        if (!finalApproval) {
            throw new IllegalArgumentException("finalApproval must be true");
        }
        if (approvedContentReference == null || approvedContentReference.isBlank()) {
            throw new IllegalArgumentException("approvedContentReference must not be blank");
        }
    }
}

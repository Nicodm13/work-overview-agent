package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.*;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
class ActionService implements IActionService {

    private final IFollowUpService followUpService;
    private final Map<String, ActionDraft> draftsById = new LinkedHashMap<>();
    private final Map<String, Instant> approvalsByDraftId = new LinkedHashMap<>();
    private final Map<String, String> approvedContentByDraftId = new LinkedHashMap<>();
    private final List<AuditLogEntry> auditEntries = new ArrayList<>();

    ActionService(IFollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    @Override
    public synchronized CreateActionDraftResponse createDraft(CreateActionDraftRequest request) {
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
        draftsById.put(draft.id(), draft);
        return new CreateActionDraftResponse(draft);
    }

    @Override
    public synchronized ApproveActionResponse approveDraft(ApproveActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var draft = draftFor(request.ownerId(), request.draftId());
        requireFinalApproval(request.finalApproval(), request.approvedContentReference());
        var approvedAt = request.approvedAt() == null ? Instant.now() : request.approvedAt();
        approvalsByDraftId.put(draft.id(), approvedAt);
        approvedContentByDraftId.put(draft.id(), request.approvedContentReference());
        return new ApproveActionResponse(draft, true, approvedAt);
    }

    @Override
    public synchronized ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateOwnerId(request.ownerId());
        var draft = draftFor(request.ownerId(), request.draftId());
        requireFinalApproval(request.finalApproval(), request.approvedContentReference());
        if (!approvalsByDraftId.containsKey(draft.id())) {
            throw new IllegalStateException("draft must be approved before execution");
        }
        if (!request.approvedContentReference().equals(approvedContentByDraftId.get(draft.id()))) {
            throw new IllegalArgumentException("approved content does not match the approved draft");
        }

        var executedAt = request.executedAt() == null ? Instant.now() : request.executedAt();
        var auditEntry = new AuditLogEntry(
            UUID.randomUUID().toString(),
            request.ownerId(),
            draft.followUpItemId(),
            draft.actionType().name(),
            executedAt,
            "APPROVED",
            request.approvedContentReference());
        auditEntries.add(auditEntry);
        return new ExecuteApprovedActionResponse(auditEntry);
    }

    @Override
    public synchronized List<AuditLogEntry> auditLog(String ownerId) {
        validateOwnerId(ownerId);
        return auditEntries.stream().filter(entry -> entry.ownerId().equals(ownerId)).toList();
    }

    private ActionDraft draftFor(String ownerId, String draftId) {
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
        }
        var draft = draftsById.get(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("draft not found: " + draftId);
        }
        if (!draft.ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("draft does not belong to owner");
        }
        return draft;
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

package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.ApproveActionResponse;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftResponse;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse;
import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ActionService implements IActionService {

    private final Map<String, ActionDraft> draftsById = new LinkedHashMap<>();
    private final Map<String, Instant> approvalsByDraftId = new LinkedHashMap<>();
    private final Map<String, String> approvedContentByDraftId = new LinkedHashMap<>();
    private final List<AuditLogEntry> auditEntries = new ArrayList<>();

    @Override
    public synchronized CreateActionDraftResponse createDraft(CreateActionDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateDraftRequest(request);

        var draft = new ActionDraft(
                UUID.randomUUID().toString(),
                request.actionType(),
                request.evidenceId(),
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
        var draft = draftFor(request.draftId());
        requireFinalApproval(request.finalApproval(), request.approvedContentReference());
        var approvedAt = request.approvedAt() == null ? Instant.now() : request.approvedAt();
        approvalsByDraftId.put(draft.id(), approvedAt);
        approvedContentByDraftId.put(draft.id(), request.approvedContentReference());
        return new ApproveActionResponse(draft, true, approvedAt);
    }

    @Override
    public synchronized ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var draft = draftFor(request.draftId());
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
                draft.evidenceId(),
                draft.actionType().name(),
                executedAt,
                "APPROVED",
                request.approvedContentReference());
        auditEntries.add(auditEntry);
        return new ExecuteApprovedActionResponse(auditEntry);
    }

    @Override
    public synchronized List<AuditLogEntry> auditLog() {
        return List.copyOf(auditEntries);
    }

    private ActionDraft draftFor(String draftId) {
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
        }
        var draft = draftsById.get(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("draft not found: " + draftId);
        }
        return draft;
    }

    private void validateDraftRequest(CreateActionDraftRequest request) {
        Objects.requireNonNull(request.actionType(), "actionType must not be null");
        if (request.evidenceId() == null || request.evidenceId().isBlank()) {
            throw new IllegalArgumentException("evidenceId must not be blank");
        }
        if (request.actionType() == ActionType.MEETING_INVITATION
                && request.selectedStartsAt() != null
                && request.selectedEndsAt() != null
                && request.selectedEndsAt().isBefore(request.selectedStartsAt())) {
            throw new IllegalArgumentException("selectedEndsAt must not be before selectedStartsAt");
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

package dk.school.workoverviewagent.action.api;

import dk.school.workoverviewagent.action.contract.*;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.AuditLogEntry;

import java.util.List;

public interface IActionService {

    CreateActionDraftResponse createDraft(CreateActionDraftRequest request);

    ApproveActionResponse approveDraft(ApproveActionRequest request);

    ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request);

    ActionDraft getActionDraft(String ownerId, String draftId);

    List<ActionDraft> listActiveActionDrafts(String ownerId);

    void deleteDraft(String ownerId, String draftId);

    List<AuditLogEntry> auditLog(String ownerId);
}

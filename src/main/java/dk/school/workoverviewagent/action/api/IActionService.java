package dk.school.workoverviewagent.action.api;

import dk.school.workoverviewagent.action.contract.*;
import dk.school.workoverviewagent.model.AuditLogEntry;

import java.util.List;

public interface IActionService {

    CreateActionDraftResponse createDraft(CreateActionDraftRequest request);

    ApproveActionResponse approveDraft(ApproveActionRequest request);

    ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request);

    List<AuditLogEntry> auditLog(String ownerId);
}

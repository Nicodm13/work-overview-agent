package dk.school.workoverviewagent.action.api;

import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.ApproveActionResponse;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftResponse;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse;
import dk.school.workoverviewagent.model.AuditLogEntry;
import java.util.List;

public interface IActionService {

    CreateActionDraftResponse createDraft(CreateActionDraftRequest request);

    ApproveActionResponse approveDraft(ApproveActionRequest request);

    ExecuteApprovedActionResponse executeApprovedAction(ExecuteApprovedActionRequest request);

    List<AuditLogEntry> auditLog();
}

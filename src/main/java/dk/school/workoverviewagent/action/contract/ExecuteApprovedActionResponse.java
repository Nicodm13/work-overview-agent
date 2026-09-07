package dk.school.workoverviewagent.action.contract;

import dk.school.workoverviewagent.model.AuditLogEntry;

public record ExecuteApprovedActionResponse(AuditLogEntry auditLogEntry) {
}

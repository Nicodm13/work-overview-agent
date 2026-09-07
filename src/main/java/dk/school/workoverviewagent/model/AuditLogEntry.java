package dk.school.workoverviewagent.model;

import java.time.Instant;

public record AuditLogEntry(
        String id,
        String evidenceId,
        String actionType,
        Instant timestamp,
        String approvalStatus,
        String approvedContentReference) {
}

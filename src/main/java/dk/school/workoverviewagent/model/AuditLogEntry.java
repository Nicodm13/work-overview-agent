package dk.school.workoverviewagent.model;

import java.time.Instant;

public record AuditLogEntry(
        String id,
        String followUpItemId,
        String actionType,
        Instant timestamp,
        String approvalStatus,
        String approvedContentReference) {
}

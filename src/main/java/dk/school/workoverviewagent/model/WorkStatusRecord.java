package dk.school.workoverviewagent.model;

import java.time.Instant;

public record WorkStatusRecord(
        String id,
        String ownerId,
        String followUpItemId,
        WorkStatus status,
        String reason,
        StatusSource statusSource,
        Instant updatedAt) {
}

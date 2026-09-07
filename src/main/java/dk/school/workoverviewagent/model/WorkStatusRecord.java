package dk.school.workoverviewagent.model;

import java.time.Instant;

public record WorkStatusRecord(
        String id,
        String evidenceId,
        WorkStatus status,
        String reason,
        StatusSource statusSource,
        Instant updatedAt) {
}

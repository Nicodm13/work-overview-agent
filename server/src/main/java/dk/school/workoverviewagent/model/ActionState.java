package dk.school.workoverviewagent.model;

import java.time.Instant;

public record ActionState(
    String ownerId,
    String draftId,
    ActionStatus status,
    String approvedContentReference,
    Instant updatedAt) {
}

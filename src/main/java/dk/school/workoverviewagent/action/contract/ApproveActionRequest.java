package dk.school.workoverviewagent.action.contract;

import java.time.Instant;

public record ApproveActionRequest(
    String ownerId,
    String draftId,
    boolean finalApproval,
    String approvedContentReference,
    Instant approvedAt) {
}

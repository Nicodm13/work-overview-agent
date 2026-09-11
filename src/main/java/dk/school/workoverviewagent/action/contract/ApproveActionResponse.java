package dk.school.workoverviewagent.action.contract;

import dk.school.workoverviewagent.model.ActionDraft;

import java.time.Instant;

public record ApproveActionResponse(
    ActionDraft draft,
    boolean approved,
    Instant approvedAt) {
}

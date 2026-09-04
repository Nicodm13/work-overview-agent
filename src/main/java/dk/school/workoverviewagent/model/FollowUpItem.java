package dk.school.workoverviewagent.model;

public record FollowUpItem(
        String id,
        String title,
        String summary,
        EvidenceStatus evidenceStatus,
        String suggestedNextAction) {
}

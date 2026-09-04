package dk.school.workoverviewagent.review.contract;

public record OverviewItem(
        String id,
        String title,
        String summary,
        String priority,
        String evidenceStatus,
        String workStatus,
        String suggestedNextAction) {
}

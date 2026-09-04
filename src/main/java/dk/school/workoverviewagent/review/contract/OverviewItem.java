package dk.school.workoverviewagent.review.contract;

import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;

public record OverviewItem(
        String id,
        String title,
        String summary,
        String priority,
        EvidenceStatus evidenceStatus,
        WorkStatus workStatus,
        StatusSource statusSource,
        String suggestedNextAction) {
}

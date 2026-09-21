package dk.school.workoverviewagent.review.contract;

import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;

import java.util.List;

public record OverviewItem(
    String id,
    String title,
    String summary,
    String priority,
    EvidenceStatus evidenceStatus,
    List<EvidenceReference> evidenceReferences,
    WorkStatus workStatus,
    StatusSource statusSource) {

    public OverviewItem {
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }
}

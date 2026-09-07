package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;

public record StatusItem(
        EvidenceItem evidenceItem,
        WorkStatus workStatus,
        StatusSource statusSource) {
}

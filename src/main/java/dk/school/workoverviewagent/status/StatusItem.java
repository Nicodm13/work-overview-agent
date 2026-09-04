package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.evidence.EvidenceItem;

public record StatusItem(
        EvidenceItem evidenceItem,
        String workStatus) {
}

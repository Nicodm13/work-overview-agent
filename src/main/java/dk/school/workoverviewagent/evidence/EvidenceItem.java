package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.model.EvidenceStatus;

public record EvidenceItem(
        String id,
        String title,
        String summary,
        String priority,
        EvidenceStatus evidenceStatus) {
}

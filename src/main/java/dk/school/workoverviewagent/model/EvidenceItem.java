package dk.school.workoverviewagent.model;

import java.util.List;

public record EvidenceItem(
        String id,
        String title,
        String summary,
        EvidenceStatus evidenceStatus,
        List<EvidenceReference> references) {

    public EvidenceItem {
        references = references == null ? List.of() : List.copyOf(references);
    }
}

package dk.school.workoverviewagent.model;

import java.util.List;

public record EvidenceItem(
    String id,
    String title,
    String summary,
    EvidenceStatus evidenceStatus,
    List<EvidenceReference> evidenceReferences) {

    public EvidenceItem {
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }
}

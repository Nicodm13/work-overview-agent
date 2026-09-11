package dk.school.workoverviewagent.model;

import java.util.List;

public record FollowUpItem(
    String id,
    String ownerId,
    String title,
    String summary,
    List<EvidenceReference> evidenceReferences) {

    public FollowUpItem {
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }
}

package dk.school.workoverviewagent.followup.contract;

import dk.school.workoverviewagent.model.EvidenceReference;
import java.util.List;

public record CreateFollowUpItemRequest(
        String title,
        String summary,
        List<EvidenceReference> evidenceReferences) {

    public CreateFollowUpItemRequest {
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }
}

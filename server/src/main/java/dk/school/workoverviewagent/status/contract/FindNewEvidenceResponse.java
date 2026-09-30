package dk.school.workoverviewagent.status.contract;

import java.util.List;

public record FindNewEvidenceResponse(
    String ownerId,
    List<NewEvidenceForResolvedItem> items) {

    public FindNewEvidenceResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

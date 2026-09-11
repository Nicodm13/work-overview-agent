package dk.school.workoverviewagent.evidence.contract;

import dk.school.workoverviewagent.model.EvidenceReference;

import java.util.List;

public record EvidenceResponse(
    String ownerId,
    String evidenceId,
    List<EvidenceReference> references) {

    public EvidenceResponse {
        references = references == null ? List.of() : List.copyOf(references);
    }
}

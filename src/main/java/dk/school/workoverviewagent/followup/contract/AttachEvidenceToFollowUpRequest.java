package dk.school.workoverviewagent.followup.contract;

import dk.school.workoverviewagent.model.EvidenceReference;

public record AttachEvidenceToFollowUpRequest(
    String ownerId,
    String followUpItemId,
    EvidenceReference evidenceReference) {
}

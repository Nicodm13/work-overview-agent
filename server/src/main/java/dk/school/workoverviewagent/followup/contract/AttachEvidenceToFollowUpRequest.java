package dk.school.workoverviewagent.followup.contract;

public record AttachEvidenceToFollowUpRequest(
    String ownerId,
    String followUpItemId,
    String evidenceReferenceId) {
}

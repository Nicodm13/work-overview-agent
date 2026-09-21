package dk.school.workoverviewagent.action.contract;

public record DeleteActionDraftResponse(
    String draftId,
    boolean deleted) {
}

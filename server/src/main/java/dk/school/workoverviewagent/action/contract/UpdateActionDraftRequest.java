package dk.school.workoverviewagent.action.contract;

import java.time.Instant;
import java.util.List;

public record UpdateActionDraftRequest(
    String ownerId,
    String draftId,
    List<String> recipients,
    String subject,
    String body,
    String meetingTitle,
    Instant selectedStartsAt,
    Instant selectedEndsAt,
    List<String> agenda,
    String editableContext) {

    public UpdateActionDraftRequest {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        agenda = agenda == null ? List.of() : List.copyOf(agenda);
    }
}

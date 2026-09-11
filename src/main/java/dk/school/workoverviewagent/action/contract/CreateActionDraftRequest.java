package dk.school.workoverviewagent.action.contract;

import dk.school.workoverviewagent.model.ActionType;
import java.time.Instant;
import java.util.List;

public record CreateActionDraftRequest(
        String ownerId,
        ActionType actionType,
        String followUpItemId,
        List<String> recipients,
        String subject,
        String body,
        String meetingTitle,
        Instant selectedStartsAt,
        Instant selectedEndsAt,
        List<String> agenda,
        String editableContext) {

    public CreateActionDraftRequest {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        agenda = agenda == null ? List.of() : List.copyOf(agenda);
    }
}

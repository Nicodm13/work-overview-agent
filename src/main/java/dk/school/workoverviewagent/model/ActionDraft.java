package dk.school.workoverviewagent.model;

import java.time.Instant;
import java.util.List;

public record ActionDraft(
        String id,
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

    public ActionDraft {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        agenda = agenda == null ? List.of() : List.copyOf(agenda);
    }
}

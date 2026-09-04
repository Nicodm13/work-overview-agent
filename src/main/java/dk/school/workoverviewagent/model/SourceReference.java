package dk.school.workoverviewagent.model;

import java.time.Instant;

public record SourceReference(
        String id,
        String followUpItemId,
        String sourceId,
        SourceType sourceType,
        Instant timestamp,
        String titleOrSubject,
        String senderOrOrganizer,
        String excerpt) {
}

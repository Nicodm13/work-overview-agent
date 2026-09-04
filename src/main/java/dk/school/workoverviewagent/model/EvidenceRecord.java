package dk.school.workoverviewagent.model;

import java.time.Instant;
import java.util.List;

public record EvidenceRecord(
        String id,
        SourceType sourceType,
        Instant timestamp,
        String content,
        List<String> participants) {

    public EvidenceRecord {
        participants = participants == null ? List.of() : List.copyOf(participants);
    }
}

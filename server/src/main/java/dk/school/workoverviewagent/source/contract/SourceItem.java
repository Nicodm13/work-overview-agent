package dk.school.workoverviewagent.source.contract;

import dk.school.workoverviewagent.model.SourceType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SourceItem(
    String id,
    SourceType sourceType,
    Instant occurredAt,
    String title,
    String content,
    String senderOrOrganizer,
    List<String> participants,
    Map<String, String> attributes) {

    public SourceItem {
        participants = participants == null ? List.of() : List.copyOf(participants);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}

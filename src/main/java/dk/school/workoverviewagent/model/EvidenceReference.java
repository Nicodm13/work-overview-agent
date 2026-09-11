package dk.school.workoverviewagent.model;

import java.time.Instant;

public record EvidenceReference(
    SourceType sourceType,
    String sourceId,
    Instant timestamp,
    String author,
    String title,
    String excerpt,
    double confidence) {
}

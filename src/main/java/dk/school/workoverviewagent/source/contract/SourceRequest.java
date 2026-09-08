package dk.school.workoverviewagent.source.contract;

import dk.school.workoverviewagent.model.SourceType;
import java.time.Instant;
import java.util.List;

public record SourceRequest(
        Instant startsAt,
        Instant endsAt,
        List<SourceType> sourceTypes) {

    public SourceRequest {
        sourceTypes = sourceTypes == null ? List.of() : List.copyOf(sourceTypes);
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("endsAt must not be before startsAt");
        }
    }
}

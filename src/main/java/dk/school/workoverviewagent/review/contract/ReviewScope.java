package dk.school.workoverviewagent.review.contract;

import dk.school.workoverviewagent.model.SourceType;
import java.time.Instant;
import java.util.List;

public record ReviewScope(
        Instant startsAt,
        Instant endsAt,
        List<SourceType> sources,
        ReviewPurpose purpose,
        String filterText) {

    public ReviewScope {
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("endsAt must not be before startsAt");
        }
    }
}

package dk.school.workoverviewagent.review.contract;

import dk.school.workoverviewagent.model.SourceType;
import java.time.Instant;
import java.util.List;

public record ReviewRequest(
        Instant startsAt,
        Instant endsAt,
        List<SourceType> sources,
        ReviewPurpose purpose) {

    public ReviewRequest {
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("endsAt must not be before startsAt");
        }
    }
}

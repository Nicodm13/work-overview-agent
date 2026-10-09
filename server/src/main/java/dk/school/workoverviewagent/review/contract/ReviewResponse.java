package dk.school.workoverviewagent.review.contract;

import dk.school.workoverviewagent.source.contract.SourceCoverage;
import java.time.Instant;
import java.util.List;

public record ReviewResponse(
    String reviewId,
    ReviewRequest request,
    Instant reviewedAt,
    List<OverviewItem> items,
    List<String> limitations,
    SourceCoverage coverage) {

    public ReviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        if (coverage == null) {
            throw new IllegalArgumentException("coverage must not be null");
        }
    }
}

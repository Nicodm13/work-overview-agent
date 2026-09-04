package dk.school.workoverviewagent.review.contract;

import java.time.Instant;
import java.util.List;

public record ReviewResponse(
        String reviewId,
        ReviewScope scope,
        Instant reviewedAt,
        List<OverviewItem> items,
        List<String> limitations) {

    public ReviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}

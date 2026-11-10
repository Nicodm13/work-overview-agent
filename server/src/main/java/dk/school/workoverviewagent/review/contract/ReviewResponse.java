package dk.school.workoverviewagent.review.contract;

import dk.school.workoverviewagent.source.contract.SourceCoverage;
import dk.school.workoverviewagent.model.EvidenceItem;
import java.util.List;

public record ReviewResponse(
    ReviewRequest request,
    List<EvidenceItem> items,
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

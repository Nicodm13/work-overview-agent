package dk.school.workoverviewagent.review;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceCoverage;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ReviewService implements IReviewService {

    private final ISourceAdapterLayer sourceAdapterLayer;
    private final IEvidenceService evidenceService;

    public ReviewService(
        ISourceAdapterLayer sourceAdapterLayer,
        IEvidenceService evidenceService) {
        this.sourceAdapterLayer = sourceAdapterLayer;
        this.evidenceService = evidenceService;
    }

    @Override
    public ReviewResponse reviewWorkContext(ReviewRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        var sourceData = sourceAdapterLayer.loadSources(new SourceRequest(
            request.ownerId(),
            request.startsAt(),
            request.endsAt(),
            request.sources()));
        var evidenceItems = evidenceService.captureEvidence(request, sourceData);

        return new ReviewResponse(
            request,
            evidenceItems,
            sourceData == null ? List.of("No source data was returned; review is unavailable.") : sourceData.limitations(),
            sourceData == null ? SourceCoverage.FAILED : sourceData.coverage());
    }

}

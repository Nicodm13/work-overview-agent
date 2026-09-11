package dk.school.workoverviewagent.review;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.OverviewItem;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
        var overviewItems = evidenceItems.stream()
                .map(this::toOverviewItem)
                .toList();

        return new ReviewResponse(
                UUID.randomUUID().toString(),
                request,
                Instant.now(),
                overviewItems,
                sourceData == null ? List.of() : sourceData.limitations());
    }

    private OverviewItem toOverviewItem(dk.school.workoverviewagent.model.EvidenceItem evidenceItem) {
        return new OverviewItem(
                evidenceItem.id(),
                evidenceItem.title(),
                evidenceItem.summary(),
                "UNRANKED",
                evidenceItem.evidenceStatus(),
                evidenceItem.references(),
                WorkStatus.UNVERIFIED,
                StatusSource.DIGITAL_EVIDENCE);
    }
}

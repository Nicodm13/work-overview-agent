package dk.school.workoverviewagent.review;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.model.StatusItem;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.OverviewItem;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.status.api.IStatusService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReviewService implements IReviewService {

    private final ISourceAdapterLayer sourceAdapterLayer;
    private final IEvidenceService evidenceService;
    private final IStatusService statusService;

    public ReviewService(
            ISourceAdapterLayer sourceAdapterLayer,
            IEvidenceService evidenceService,
            IStatusService statusService) {
        this.sourceAdapterLayer = sourceAdapterLayer;
        this.evidenceService = evidenceService;
        this.statusService = statusService;
    }

    @Override
    public ReviewResponse reviewWorkContext(ReviewRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        var sourceData = sourceAdapterLayer.loadSources(new SourceRequest(
                request.startsAt(),
                request.endsAt(),
                request.sources(),
                null));
        var evidenceItems = evidenceService.captureEvidence(request, sourceData);
        var statusItems = statusService.applyCurrentStatus(evidenceItems);
        var overviewItems = statusItems.stream()
                .map(this::toOverviewItem)
                .toList();

        return new ReviewResponse(
                UUID.randomUUID().toString(),
                request,
                Instant.now(),
                overviewItems,
                sourceData == null ? List.of() : sourceData.limitations());
    }

    private OverviewItem toOverviewItem(StatusItem statusItem) {
        var evidenceItem = statusItem.evidenceItem();
        return new OverviewItem(
                evidenceItem.id(),
                evidenceItem.title(),
                evidenceItem.summary(),
                "UNRANKED",
                evidenceItem.evidenceStatus(),
                evidenceItem.references(),
                statusItem.workStatus(),
                statusItem.statusSource());
    }
}

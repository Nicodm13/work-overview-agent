package dk.school.workoverviewagent.review;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
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
    private final IActionService actionService;

    public ReviewService(
            ISourceAdapterLayer sourceAdapterLayer,
            IEvidenceService evidenceService,
            IStatusService statusService,
            IActionService actionService) {
        this.sourceAdapterLayer = sourceAdapterLayer;
        this.evidenceService = evidenceService;
        this.statusService = statusService;
        this.actionService = actionService;
    }

    @Override
    public ReviewResponse reviewWorkContext(ReviewRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.scope(), "review scope must not be null");

        var sourceData = sourceAdapterLayer.loadSources(request.userId(), request.scope());
        var evidenceItems = evidenceService.identifyFollowUps(request.userId(), request.scope(), sourceData);
        var statusItems = statusService.applyCurrentStatus(request.userId(), evidenceItems);
        var overviewItems = actionService.addSuggestedActions(request.userId(), statusItems);

        return null;
    }
}

package dk.school.workoverviewagent.review;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceCoverage;
import dk.school.workoverviewagent.source.contract.SourceData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    @Test
    void exposesFailedCoverageInsteadOfTreatingTotalFailureAsAnEmptyReview() {
        var sourceAdapterLayer = mock(ISourceAdapterLayer.class);
        var evidenceService = mock(IEvidenceService.class);
        var limitation = "OUTLOOK source could not be loaded; its results are unavailable.";
        when(sourceAdapterLayer.loadSources(any())).thenReturn(new SourceData(
            List.of(), List.of(limitation, "All selected sources failed; review is unavailable."),
            SourceCoverage.FAILED));
        when(evidenceService.captureEvidence(any(), any())).thenReturn(List.of());

        var response = new ReviewService(sourceAdapterLayer, evidenceService).reviewWorkContext(request());

        assertThat(response.items()).isEmpty();
        assertThat(response.coverage()).isEqualTo(SourceCoverage.FAILED);
        assertThat(response.limitations()).contains(limitation);
    }

    @Test
    void exposesPartialCoverageAlongsideSuccessfulEvidence() {
        var sourceAdapterLayer = mock(ISourceAdapterLayer.class);
        var evidenceService = mock(IEvidenceService.class);
        when(sourceAdapterLayer.loadSources(any())).thenReturn(new SourceData(
            List.of(), List.of("TEAMS source could not be loaded; its results are unavailable."),
            SourceCoverage.PARTIAL));
        when(evidenceService.captureEvidence(any(), any())).thenReturn(List.of(new EvidenceItem(
            "evidence-1", "Mail subject", "Captured from Outlook",
            EvidenceStatus.SOURCE_EVIDENCE_CAPTURED, List.of())));

        var response = new ReviewService(sourceAdapterLayer, evidenceService).reviewWorkContext(request());

        assertThat(response.items()).extracting("title").containsExactly("Mail subject");
        assertThat(response.coverage()).isEqualTo(SourceCoverage.PARTIAL);
        assertThat(response.limitations()).hasSize(1);
    }

    private ReviewRequest request() {
        return new ReviewRequest("owner-1", Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-09-02T00:00:00Z"), List.of(SourceType.OUTLOOK),
            ReviewPurpose.DAILY_OVERVIEW);
    }
}

package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.repository.IEvidenceRepository;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceCoverage;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EvidenceServiceMeetingTest {

    @Test
    void returnsMinuteTextForReviewButPersistsOnlyAShortExcerpt() {
        var repository = mock(IEvidenceRepository.class);
        var text = "Decision: use the ITS tenant. " + "Action: prepare the deployment review. ".repeat(10);
        var source = new SourceItem("mail-1:attachment-1", SourceType.MEETING_NOTES,
            Instant.parse("2026-10-15T12:00:00Z"), "Minutes.docx", text,
            "cortex@example.com", List.of(), Map.of("origin", "OUTLOOK"));
        var request = new ReviewRequest("tenant:user", Instant.parse("2026-10-15T00:00:00Z"),
            Instant.parse("2026-10-16T00:00:00Z"), List.of(SourceType.MEETING_NOTES),
            ReviewPurpose.DAILY_OVERVIEW);

        var result = new EvidenceService(repository).captureEvidence(request,
            new SourceData(List.of(source), List.of(), SourceCoverage.COMPLETE));

        assertThat(result).singleElement().satisfies(item -> assertThat(item.summary()).isEqualTo(text));
        var saved = ArgumentCaptor.forClass(EvidenceReference.class);
        verify(repository).save(org.mockito.ArgumentMatchers.eq("tenant:user"), saved.capture());
        assertThat(saved.getValue().excerpt()).hasSizeLessThanOrEqualTo(160);
        assertThat(saved.getValue().excerpt()).isNotEqualTo(text);
    }
}

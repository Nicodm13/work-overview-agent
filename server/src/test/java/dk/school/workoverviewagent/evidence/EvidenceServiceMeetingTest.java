package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.repository.IEvidenceRepository;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.SourceType;
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
import static org.mockito.Mockito.times;

class EvidenceServiceMeetingTest {

    @Test
    void returnsMinuteTextForReviewButPersistsOnlyAShortExcerpt() {
        var repository = mock(IEvidenceRepository.class);
        var text = "Decision: use the ITS tenant. " + "Action: prepare the deployment review. ".repeat(10);
        var source = new SourceItem("mail-1:attachment-1", SourceType.MEETING_NOTES,
            Instant.parse("2026-10-15T12:00:00Z"), "Minutes.docx", text,
            "cortex@example.com", List.of(), Map.of("origin", "OUTLOOK"));
        var request = new ReviewRequest("tenant:user", Instant.parse("2026-10-15T00:00:00Z"),
            Instant.parse("2026-10-16T00:00:00Z"), List.of(SourceType.MEETING_NOTES));

        var result = new EvidenceService(repository).captureEvidence(request,
            new SourceData(List.of(source), List.of(), SourceCoverage.COMPLETE));

        assertThat(result).singleElement().satisfies(item -> assertThat(item.summary()).isEqualTo(text));
        var saved = ArgumentCaptor.forClass(EvidenceReference.class);
        verify(repository).save(org.mockito.ArgumentMatchers.eq("tenant:user"), saved.capture());
        assertThat(saved.getValue().excerpt()).hasSizeLessThanOrEqualTo(160);
        assertThat(saved.getValue().excerpt()).isNotEqualTo(text);
    }

    @Test
    void returnsSourceContentForReviewWhilePersistingOnlyExcerpts() {
        var repository = mock(IEvidenceRepository.class);
        var text = "Context ".repeat(30) + "Please respond by Friday.";
        var timestamp = Instant.parse("2026-10-15T12:00:00Z");
        var sources = List.of(SourceType.OUTLOOK, SourceType.TEAMS, SourceType.CALENDAR).stream()
            .map(type -> new SourceItem(type.name(), type, timestamp, "Subject", text,
                "sender@example.com", List.of(), Map.of()))
            .toList();
        var request = new ReviewRequest("tenant:user", timestamp.minusSeconds(1), timestamp.plusSeconds(1),
            List.of());

        var result = new EvidenceService(repository).captureEvidence(request,
            new SourceData(sources, List.of(), SourceCoverage.COMPLETE));

        assertThat(result).extracting(item -> item.summary()).containsExactly(text, text, text);
        var saved = ArgumentCaptor.forClass(EvidenceReference.class);
        verify(repository, times(3)).save(org.mockito.ArgumentMatchers.eq("tenant:user"), saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(reference -> {
            assertThat(reference.excerpt()).hasSizeLessThanOrEqualTo(160);
            assertThat(reference.excerpt()).doesNotContain("Please respond by Friday.");
        });
    }
}

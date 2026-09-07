package dk.school.workoverviewagent.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceServiceTest {

    private final EvidenceService evidenceService = new EvidenceService();

    @Test
    void shouldCreateEvidenceRecordsFromNormalizedSourceItems() {
        var sourceItem = new SourceItem(
                "teams-1",
                SourceType.TEAMS,
                Instant.parse("2026-09-04T08:20:00Z"),
                "Test environment clarification",
                "Can you confirm whether the test environment is ready for use?",
                "Maja Jensen",
                List.of("Maja Jensen"),
                Map.of("channel", "Project Alpha"));

        var evidenceItems = evidenceService.captureEvidence(reviewRequest(), new SourceData(List.of(sourceItem), List.of()));

        assertThat(evidenceItems).hasSize(1);
        var evidence = evidenceItems.getFirst();
        assertThat(evidence.id()).isEqualTo("evidence-teams-1");
        assertThat(evidence.title()).isEqualTo("Test environment clarification");
        assertThat(evidence.summary()).isEqualTo("Source evidence captured from TEAMS.");
        assertThat(evidence.evidenceStatus()).isEqualTo(EvidenceStatus.SOURCE_EVIDENCE_CAPTURED);
        assertThat(evidence.references()).singleElement().satisfies(reference -> {
            assertThat(reference.sourceType()).isEqualTo(SourceType.TEAMS);
            assertThat(reference.sourceId()).isEqualTo("teams-1");
            assertThat(reference.timestamp()).isEqualTo(Instant.parse("2026-09-04T08:20:00Z"));
            assertThat(reference.author()).isEqualTo("Maja Jensen");
            assertThat(reference.title()).isEqualTo("Test environment clarification");
            assertThat(reference.excerpt()).contains("Can you confirm");
            assertThat(reference.confidence()).isEqualTo(1.0);
        });
    }

    @Test
    void shouldNotInferMeaningFromSourceText() {
        var sourceItem = new SourceItem(
                "email-1",
                SourceType.OUTLOOK,
                Instant.parse("2026-09-04T09:05:00Z"),
                "Decision needed: integration approach",
                "Please send your recommendation for the integration approach before Friday noon.",
                "Sara Lind",
                List.of("Sara Lind"),
                Map.of("importance", "high"));

        var evidenceItems = evidenceService.captureEvidence(reviewRequest(), new SourceData(List.of(sourceItem), List.of()));

        assertThat(evidenceItems).hasSize(1);
        var evidence = evidenceItems.getFirst();
        assertThat(evidence.evidenceStatus()).isEqualTo(EvidenceStatus.SOURCE_EVIDENCE_CAPTURED);
        assertThat(evidence.summary()).isEqualTo("Source evidence captured from OUTLOOK.");
    }

    @Test
    void shouldSupportEvidenceLookupForLatestIdentifiedEvidence() {
        evidenceService.captureEvidence(
                reviewRequest(),
                new SourceData(
                        List.of(new SourceItem(
                                "meeting-notes-1",
                                SourceType.MEETING_NOTES,
                                Instant.parse("2026-09-03T14:00:00Z"),
                                "Project Alpha planning notes",
                                "Maja will investigate whether the test environment can be used.",
                                "Maja Jensen",
                                List.of("Maja Jensen", "Jonas Holm"),
                                Map.of("meetingTitle", "Project Alpha planning"))),
                        List.of()));

        var evidence = evidenceService.getEvidence("evidence-meeting-notes-1");

        assertThat(evidence.evidenceId()).isEqualTo("evidence-meeting-notes-1");
        assertThat(evidence.references())
                .singleElement()
                .satisfies(reference -> assertThat(reference.sourceId()).isEqualTo("meeting-notes-1"));
    }

    private ReviewRequest reviewRequest() {
        return new ReviewRequest(
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T23:59:59Z"),
                List.of(),
                ReviewPurpose.DAILY_OVERVIEW);
    }
}

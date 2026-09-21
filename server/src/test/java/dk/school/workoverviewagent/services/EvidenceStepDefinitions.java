package dk.school.workoverviewagent.services;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EvidenceStepDefinitions {
    @Autowired
    private IEvidenceService evidenceService;
    private List<dk.school.workoverviewagent.model.EvidenceItem> evidenceItems;

    @Given("a normalized Teams source item with id {string}")
    public void normalizedTeamsSourceItem(String id) {
        var item = new SourceItem(
            id,
            SourceType.TEAMS,
            Instant.parse("2026-09-04T08:20:00Z"),
            "Test environment clarification",
            "Can you confirm readiness?",
            "Maja Jensen",
            List.of("Maja Jensen"),
            Map.of("channel", "Project Alpha"));
        var request = new ReviewRequest(
            "user-1",
            Instant.parse("2026-09-04T00:00:00Z"),
            Instant.parse("2026-09-04T23:59:59Z"),
            List.of(),
            ReviewPurpose.DAILY_OVERVIEW);
        evidenceItems = evidenceService.captureEvidence(request, new SourceData(List.of(item), List.of()));
    }

    @Then("the evidence is captured with source type {string} and status {string}")
    public void evidenceCaptured(String sourceType, String status) {
        assertThat(evidenceItems).singleElement().satisfies(item -> {
            assertThat(item.references()).singleElement().satisfies(reference ->
                assertThat(reference.sourceType()).isEqualTo(SourceType.valueOf(sourceType)));
            assertThat(item.evidenceStatus()).isEqualTo(EvidenceStatus.valueOf(status));
        });
    }
}

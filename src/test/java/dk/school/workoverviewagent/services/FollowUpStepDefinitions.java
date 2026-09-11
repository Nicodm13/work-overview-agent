package dk.school.workoverviewagent.services;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.SourceType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FollowUpStepDefinitions {
    @Autowired
    private IFollowUpService followUpService;
    private dk.school.workoverviewagent.model.FollowUpItem followUpItem;

    @Given("a follow-up item with Teams evidence")
    public void followUpItemWithTeamsEvidence() {
        followUpItem = followUpService.createFollowUpItem(new CreateFollowUpItemRequest("user-1",
            "Test environment clarification",
            "Possible follow-up based on selected evidence.",
            List.of(reference(SourceType.TEAMS, "teams-1"))));
    }

    @When("email evidence is attached to the follow-up item")
    public void attachEmailEvidence() {
        followUpItem = followUpService.attachEvidence(new AttachEvidenceToFollowUpRequest(
            "user-1", followUpItem.id(), reference(SourceType.OUTLOOK, "email-1")));
    }

    @Then("the follow-up item contains Teams and OUTLOOK evidence")
    public void followUpContainsEvidenceFromBothChannels() {
        assertThat(followUpItem.id()).isNotBlank();
        assertThat(followUpItem.evidenceReferences()).extracting(EvidenceReference::sourceType)
            .containsExactly(SourceType.TEAMS, SourceType.OUTLOOK);
    }

    private EvidenceReference reference(SourceType sourceType, String sourceId) {
        return new EvidenceReference(sourceType, sourceId, Instant.parse("2026-09-04T08:20:00Z"), "Maja Jensen", "Test environment clarification", "Selected source evidence.", 1.0);
    }
}

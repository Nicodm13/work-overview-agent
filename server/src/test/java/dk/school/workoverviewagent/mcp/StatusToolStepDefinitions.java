package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.evidence.repository.IEvidenceRepository;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.FollowUpItem;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.status.contract.FindNewEvidenceResponse;
import dk.school.workoverviewagent.status.contract.GetWorkStatusResponse;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusResponse;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusToolStepDefinitions {

    @Autowired
    private FollowUpTools followUpTools;
    @Autowired
    private StatusTools statusTools;
    @Autowired
    private IEvidenceRepository evidenceRepository;

    private FollowUpItem createdFollowUpItem;
    private UpdateWorkStatusResponse statusUpdateResponse;
    private GetWorkStatusResponse statusResponse;
    private FindNewEvidenceResponse newEvidenceResponse;

    @When("the status tool scenario creates a follow-up item with title {string}")
    public void createFollowUpItem(String title) {
        createdFollowUpItem = followUpTools.createFollowUpItem(title, "Created through the MCP tool.");
    }

    @When("the update_status MCP tool marks the created follow-up item as {string} with reason {string}")
    public void updateStatus(String workStatus, String reason) {
        statusUpdateResponse = statusTools.updateStatus(
            createdFollowUpItem.id(),
            WorkStatus.valueOf(workStatus),
            reason);
    }

    @Then("the status update is {string} from {string}")
    public void statusUpdateIs(String workStatus, String statusSource) {
        assertThat(statusUpdateResponse.workStatus()).isEqualTo(WorkStatus.valueOf(workStatus));
        assertThat(statusUpdateResponse.statusSource()).isEqualTo(StatusSource.valueOf(statusSource));
    }

    @When("the get_status MCP tool is called for the created follow-up item")
    public void getStatus() {
        statusResponse = statusTools.getStatus(createdFollowUpItem.id());
    }

    @When("later email evidence is linked to the created follow-up item")
    public void linkLaterEmailEvidence() {
        var reference = new EvidenceReference(
                "later-email-evidence",
                SourceType.OUTLOOK,
                "email-later-1",
                Instant.parse("2099-01-01T08:30:00Z"),
                "Maja Jensen",
                "New question about the resolved item",
                "Can you help with this again?",
                1.0);
        evidenceRepository.save(createdFollowUpItem.ownerId(), reference);
        followUpTools.attachEvidenceToFollowUp(createdFollowUpItem.id(), reference.id());
    }

    @When("the find_new_evidence MCP tool is called")
    public void findNewEvidence() {
        newEvidenceResponse = statusTools.findNewEvidence();
    }

    @Then("the new evidence result contains the created follow-up item")
    public void newEvidenceResultContainsCreatedFollowUpItem() {
        assertThat(newEvidenceResponse.items())
            .extracting(item -> item.followUpItemId())
            .containsExactly(createdFollowUpItem.id());
    }

    @Then("the new evidence result is empty")
    public void newEvidenceResultIsEmpty() {
        assertThat(newEvidenceResponse.items()).isEmpty();
    }

    @Then("the retrieved status is {string} from {string}")
    public void retrievedStatusIs(String workStatus, String statusSource) {
        assertThat(statusResponse.workStatus()).isEqualTo(WorkStatus.valueOf(workStatus));
        assertThat(statusResponse.statusSource()).isEqualTo(StatusSource.valueOf(statusSource));
    }
}

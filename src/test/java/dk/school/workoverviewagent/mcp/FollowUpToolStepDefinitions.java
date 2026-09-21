package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.FollowUpItem;
import dk.school.workoverviewagent.model.SourceType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class FollowUpToolStepDefinitions {

    @Autowired
    private FollowUpTools followUpTools;

    private FollowUpItem createdFollowUpItem;
    private FollowUpItem retrievedFollowUpItem;
    private FollowUpItem followUpItemWithEvidence;
    private List<FollowUpItem> followUpItems;

    @When("the create_follow_up_item MCP tool is called with title {string}")
    public void createFollowUpItem(String title) {
        createdFollowUpItem = followUpTools.createFollowUpItem(title, "Created through the MCP tool.");
    }

    @Then("the created follow-up item has no evidence references")
    public void createdFollowUpItemHasNoEvidenceReferences() {
        assertThat(createdFollowUpItem.evidenceReferences()).isEmpty();
    }

    @When("the get_follow_up_item MCP tool is called for the created follow-up item")
    public void getFollowUpItem() {
        retrievedFollowUpItem = followUpTools.getFollowUpItem(createdFollowUpItem.id());
    }

    @Then("the retrieved follow-up item matches the created follow-up item")
    public void retrievedFollowUpItemMatchesCreatedFollowUpItem() {
        assertThat(retrievedFollowUpItem).isEqualTo(createdFollowUpItem);
    }

    @When("the attach_evidence_to_follow_up MCP tool is called with Teams evidence")
    public void attachEvidenceToFollowUp() {
        followUpItemWithEvidence = followUpTools.attachEvidenceToFollowUp(
            createdFollowUpItem.id(),
            new EvidenceReference(
                "mcp-tool-teams-evidence",
                SourceType.TEAMS,
                "teams-mcp-tool-1",
                Instant.parse("2026-09-04T08:20:00Z"),
                "Maja Jensen",
                "Test environment clarification",
                "Can you confirm whether the test environment is ready for use?",
                1.0));
    }

    @Then("the follow-up item has {int} explicitly linked evidence reference")
    public void followUpItemHasExplicitlyLinkedEvidenceReferences(int count) {
        assertThat(followUpItemWithEvidence.evidenceReferences()).hasSize(count);
    }

    @When("the list_follow_up_items MCP tool is called")
    public void listFollowUpItems() {
        followUpItems = followUpTools.listFollowUpItems();
    }

    @Then("the list contains the created follow-up item")
    public void listContainsCreatedFollowUpItem() {
        assertThat(followUpItems).extracting(FollowUpItem::id).contains(createdFollowUpItem.id());
    }
}

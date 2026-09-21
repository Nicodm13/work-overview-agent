package dk.school.workoverviewagent.mcp;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class McpToolStepDefinitions {
    @Autowired
    private ReviewTools reviewTools;
    @Autowired
    private EvidenceTools evidenceTools;
    @Autowired
    private FollowUpTools followUpTools;

    private dk.school.workoverviewagent.review.contract.ReviewResponse response;
    private dk.school.workoverviewagent.evidence.contract.EvidenceResponse evidenceResponse;
    private dk.school.workoverviewagent.model.FollowUpItem createdFollowUpItem;
    private dk.school.workoverviewagent.model.FollowUpItem retrievedFollowUpItem;
    private dk.school.workoverviewagent.model.FollowUpItem followUpItemWithEvidence;
    private List<dk.school.workoverviewagent.model.FollowUpItem> followUpItems;

    @When("the get_review MCP tool is called from {string} to {string}")
    public void getReview(String startsAt, String endsAt) {
        response = reviewTools.getReview(startsAt, endsAt);
    }

    @Then("the MCP response contains {int} review items")
    public void responseContainsItems(int count) {
        assertThat(response.items()).hasSize(count);
    }

    @Then("the MCP response uses daily overview purpose")
    public void responseUsesDailyOverview() {
        assertThat(response.request().purpose().name()).isEqualTo("DAILY_OVERVIEW");
        assertThat(response.request().sources()).isEmpty();
    }

    @When("the get_item_evidence MCP tool is called for the first review item")
    public void getItemEvidenceForFirstReviewItem() {
        var evidenceReferenceId = response.items().getFirst().evidenceReferences().getFirst().id();
        evidenceResponse = evidenceTools.getItemEvidence(evidenceReferenceId);
    }

    @Then("the evidence MCP response contains {int} source reference")
    public void evidenceResponseContainsSourceReferences(int count) {
        assertThat(evidenceResponse.references()).hasSize(count);
    }

    @Then("the evidence MCP response has source type {string}")
    public void evidenceResponseHasSourceType(String sourceType) {
        assertThat(evidenceResponse.references()).allSatisfy(reference ->
            assertThat(reference.sourceType().name()).isEqualTo(sourceType));
    }

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
            new dk.school.workoverviewagent.model.EvidenceReference(
                "mcp-tool-teams-evidence",
                dk.school.workoverviewagent.model.SourceType.TEAMS,
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
        assertThat(followUpItems).extracting(dk.school.workoverviewagent.model.FollowUpItem::id)
            .contains(createdFollowUpItem.id());
    }
}

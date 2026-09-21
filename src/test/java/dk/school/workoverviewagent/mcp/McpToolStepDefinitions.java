package dk.school.workoverviewagent.mcp;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class McpToolStepDefinitions {
    @Autowired
    private ReviewTools reviewTools;
    @Autowired
    private EvidenceTools evidenceTools;

    private dk.school.workoverviewagent.review.contract.ReviewResponse response;
    private dk.school.workoverviewagent.evidence.contract.EvidenceResponse evidenceResponse;

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
}

package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.evidence.contract.EvidenceResponse;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class EvidenceToolStepDefinitions {

    @Autowired
    private ReviewTools reviewTools;
    @Autowired
    private EvidenceTools evidenceTools;

    private ReviewResponse reviewResponse;
    private EvidenceResponse evidenceResponse;

    @When("the evidence tool scenario requests a review from {string} to {string}")
    public void getReview(String startsAt, String endsAt) {
        reviewResponse = reviewTools.getReview(startsAt, endsAt);
    }

    @When("the get_item_evidence MCP tool is called for the first review item")
    public void getItemEvidenceForFirstReviewItem() {
        var evidenceReferenceId = reviewResponse.items().getFirst().evidenceReferences().getFirst().id();
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

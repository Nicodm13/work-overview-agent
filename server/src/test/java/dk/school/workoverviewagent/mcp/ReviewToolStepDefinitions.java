package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.model.SourceType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class ReviewToolStepDefinitions {

    @Autowired
    private ReviewTools reviewTools;
    @Autowired
    private Instant mockEvidenceTimestamp;

    private ReviewResponse response;

    @When("the get_review MCP tool is called for the selected mock evidence")
    public void getReview() {
        response = reviewTools.getReview(mockEvidenceTimestamp.toString(), mockEvidenceTimestamp.toString(), null);
    }

    @When("the get_review MCP tool is called for source type {string}")
    public void getReviewWithSelectedSourceType(String sourceType) {
        response = reviewTools.getReview(
            mockEvidenceTimestamp.toString(),
            mockEvidenceTimestamp.toString(),
            java.util.List.of(SourceType.valueOf(sourceType)));
    }

    @Then("the MCP response contains {int} review items")
    public void responseContainsItems(int count) {
        assertThat(response.items()).hasSize(count);
    }

    @Then("the MCP response contains {int} review item")
    public void responseContainsSingleItem(int count) {
        assertThat(response.items()).hasSize(count);
    }

    @Then("the MCP response uses all sources")
    public void responseUsesAllSources() {
        assertThat(response.request().sources()).isEmpty();
    }

    @Then("the MCP response uses source type {string}")
    public void responseUsesSelectedSourceType(String sourceType) {
        assertThat(response.request().sources()).containsExactly(SourceType.valueOf(sourceType));
        assertThat(response.items()).allSatisfy(item ->
            assertThat(item.evidenceReferences()).allSatisfy(reference ->
                assertThat(reference.sourceType()).isEqualTo(SourceType.valueOf(sourceType))));
    }
}

package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class ReviewToolStepDefinitions {

    @Autowired
    private ReviewTools reviewTools;

    private ReviewResponse response;

    @When("the get_review MCP tool is called from {string} to {string}")
    public void getReview(String startsAt, String endsAt) {
        response = reviewTools.getReview(startsAt, endsAt, null, null);
    }

    @When("the get_review MCP tool is called for source type {string} with purpose {string}")
    public void getReviewWithSelectedSourceType(String sourceType, String reviewPurpose) {
        response = reviewTools.getReview(
            "2026-09-04T00:00:00Z",
            "2026-09-04T23:59:59Z",
            java.util.List.of(SourceType.valueOf(sourceType)),
            ReviewPurpose.valueOf(reviewPurpose));
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

    @Then("the MCP response uses source type {string} and purpose {string}")
    public void responseUsesSelectedSourceTypeAndPurpose(String sourceType, String reviewPurpose) {
        assertThat(response.request().sources()).containsExactly(SourceType.valueOf(sourceType));
        assertThat(response.request().purpose()).isEqualTo(ReviewPurpose.valueOf(reviewPurpose));
        assertThat(response.items()).allSatisfy(item ->
            assertThat(item.evidenceReferences()).allSatisfy(reference ->
                assertThat(reference.sourceType()).isEqualTo(SourceType.valueOf(sourceType))));
    }
}

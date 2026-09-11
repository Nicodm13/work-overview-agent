package dk.school.workoverviewagent.mcp;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class McpToolStepDefinitions {
    @Autowired
    private ReviewTools reviewTools;
    private dk.school.workoverviewagent.review.contract.ReviewResponse response;

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
}

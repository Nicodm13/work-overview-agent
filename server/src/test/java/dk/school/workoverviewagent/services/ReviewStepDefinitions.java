package dk.school.workoverviewagent.services;

import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class ReviewStepDefinitions {
    @Autowired
    private IReviewService reviewService;
    @Autowired
    private Instant mockEvidenceTimestamp;
    private dk.school.workoverviewagent.review.contract.ReviewResponse review;

    @When("the review service reviews the selected mock evidence")
    public void reviewInterval() {
        review = reviewService.reviewWorkContext(new ReviewRequest(
            "user-1",
            mockEvidenceTimestamp,
            mockEvidenceTimestamp,
            List.of()));
    }

    @Then("the review contains the titles {string}")
    public void reviewContainsTitles(String titles) {
        assertThat(review.items()).extracting("title").containsExactlyElementsOf(List.of(titles.split(",")));
    }
}

package dk.school.workoverviewagent.services;

import static org.assertj.core.api.Assertions.assertThat;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class ReviewStepDefinitions {
    @Autowired private IReviewService reviewService;
    private dk.school.workoverviewagent.review.contract.ReviewResponse review;

    @When("the review service reviews {string} to {string}")
    public void reviewInterval(String startsAt, String endsAt) {
        review = reviewService.reviewWorkContext(new ReviewRequest(Instant.parse(startsAt), Instant.parse(endsAt), List.of(), ReviewPurpose.DAILY_OVERVIEW));
    }

    @Then("the review contains the titles {string}")
    public void reviewContainsTitles(String titles) {
        assertThat(review.items()).extracting("title").containsExactlyElementsOf(List.of(titles.split(",")));
    }
}

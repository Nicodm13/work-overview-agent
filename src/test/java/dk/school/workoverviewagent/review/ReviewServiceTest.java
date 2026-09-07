package dk.school.workoverviewagent.review;

import static org.assertj.core.api.Assertions.assertThat;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReviewServiceTest {

    @Autowired
    private IReviewService reviewService;

    @Test
    void shouldReturnReviewResponseWithMockedSourceData() {
        ReviewRequest request = new ReviewRequest(
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T23:59:59Z"),
                List.of(SourceType.TEAMS, SourceType.OUTLOOK),
                ReviewPurpose.DAILY_OVERVIEW);

        var response = reviewService.reviewWorkContext(request);

        assertThat(response).isNotNull();
        assertThat(response.reviewId()).isNotBlank();
        assertThat(response.request()).isEqualTo(request);
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.items())
                .extracting("title")
                .containsExactly(
                        "Test environment clarification",
                        "Decision needed: integration approach",
                        "API contract follow-up");
        assertThat(response.items())
                .allSatisfy(item -> {
                    assertThat(item.workStatus()).isEqualTo(WorkStatus.UNVERIFIED);
                    assertThat(item.statusSource()).isEqualTo(StatusSource.DIGITAL_EVIDENCE);
                    assertThat(item.suggestedNextAction()).isNotBlank();
                });
        assertThat(response.limitations()).isEmpty();
    }
}

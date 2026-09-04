package dk.school.workoverviewagent.review;

import static org.assertj.core.api.Assertions.assertThat;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewScope;
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
    void shouldReturnReviewResponseWhenCollaboratorsAreNotImplementedYet() {
        ReviewRequest request = new ReviewRequest(
                "user-1",
                new ReviewScope(
                        Instant.parse("2026-09-04T00:00:00Z"),
                        Instant.parse("2026-09-04T23:59:59Z"),
                        List.of(SourceType.TEAMS, SourceType.OUTLOOK),
                        ReviewPurpose.DAILY_OVERVIEW,
                        null));

        var response = reviewService.reviewWorkContext(request);

        assertThat(response).isNotNull();
        assertThat(response.reviewId()).isNotBlank();
        assertThat(response.scope()).isEqualTo(request.scope());
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.limitations()).isEmpty();
    }
}

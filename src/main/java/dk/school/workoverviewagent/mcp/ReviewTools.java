package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.user.IUserProvider;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ReviewTools {

    private final IReviewService reviewService;
    private final IUserProvider userProvider;

    public ReviewTools(IReviewService reviewService, IUserProvider userProvider) {
        this.reviewService = reviewService;
        this.userProvider = userProvider;
    }

    @McpTool(
            name = "get_review",
            description = "Review a selected time interval across work sources and return evidence-based overview items.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true))
    public ReviewResponse getReview(
            @McpToolParam(description = "Start of the interval as an ISO-8601 instant.", required = true) String startsAt,
            @McpToolParam(description = "End of the interval as an ISO-8601 instant.", required = true) String endsAt) {
        var request = new ReviewRequest(
                userProvider.getUserId(),
                Instant.parse(startsAt),
                Instant.parse(endsAt),
                List.of(),
                ReviewPurpose.DAILY_OVERVIEW);
        return reviewService.reviewWorkContext(request);
    }
}

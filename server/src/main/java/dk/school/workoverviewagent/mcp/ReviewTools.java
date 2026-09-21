package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.api.IReviewService;
import dk.school.workoverviewagent.review.contract.ReviewPurpose;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

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
        @McpToolParam(description = "End of the interval as an ISO-8601 instant.", required = true) String endsAt,
        @McpToolParam(
            description = "Optional source types to review. Omit or provide an empty list to review all sources.",
            required = false)
        List<SourceType> sourceTypes,
        @McpToolParam(
            description = "Optional review purpose. Defaults to DAILY_OVERVIEW when omitted.",
            required = false)
        ReviewPurpose reviewPurpose) {
        var request = new ReviewRequest(
            userProvider.getUserId(),
            Instant.parse(startsAt),
            Instant.parse(endsAt),
            sourceTypes == null ? List.of() : sourceTypes,
            reviewPurpose == null ? ReviewPurpose.DAILY_OVERVIEW : reviewPurpose);
        return reviewService.reviewWorkContext(request);
    }
}

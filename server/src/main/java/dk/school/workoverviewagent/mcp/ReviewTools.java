package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.review.api.IReviewService;
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
        description = "Collect evidence for a selected interval across work sources, returning evidence items, source coverage, and limitations. Stores minimized, user-scoped references for get_item_evidence. This tool does not prioritize findings, create tracked follow-ups, retrieve their user-confirmed status, or prepare drafts; use the relevant tools separately. Missing digital updates do not establish that work is incomplete.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = true))
    public ReviewResponse getReview(
        @McpToolParam(description = "Start of the interval as an ISO-8601 instant.", required = true) String startsAt,
        @McpToolParam(description = "End of the interval as an ISO-8601 instant.", required = true) String endsAt,
        @McpToolParam(
            description = "Optional source types to review. Omit or provide an empty list to review all sources.",
            required = false)
        List<SourceType> sourceTypes) {
        var request = new ReviewRequest(
            userProvider.getUserId(),
            Instant.parse(startsAt),
            Instant.parse(endsAt),
            sourceTypes == null ? List.of() : sourceTypes);
        return reviewService.reviewWorkContext(request);
    }
}

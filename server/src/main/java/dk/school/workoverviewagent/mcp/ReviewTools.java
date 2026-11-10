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
        description = "Before concluding a review, use list_action_drafts and mention all remaining active drafts, including outdated or superseded drafts when known. Do not assume approval or execution. Review a selected time interval across work sources and return evidence-based overview items. Stores minimized, user-scoped evidence references for later retrieval with get_item_evidence. Check coverage and limitations before drawing conclusions. Before drafting, assess all returned evidence across sources for candidate follow-ups and later updates. Keep distinct requests separate even when they concern the same subject; combine evidence only when it refers to the same matter. For each finding, identify who sent the request, whom it addresses, and who agreed to act. Present the overview in separate sections: 'Your follow-ups' for actions supported by evidence as the signed-in user's responsibility, and 'Waiting on others' for relevant matters where an identified other person owns the next step. Clearly name that person and describe an update request as a suggestion, not as the user owning the underlying work. If ownership is unclear, present it separately as uncertain rather than assigning it to either section. These presentation categories do not confirm or change the user's tracked work status; only the user can do that. Do not infer that the user owns or coordinates work merely because they are a participant. When the user is waiting on a clearly identified person and an update would be useful, prepare an editable, courteous message asking that person for an update; do not assume the work is overdue or incomplete. If ownership is unclear, say so and suggest checking with an evidence-supported person when useful. After assessing and prioritizing the evidence, proactively prepare editable drafts for appropriate user actions with draft_follow_up_action. Use EMAIL for an email-based request and TEAMS_MESSAGE for a Teams-based request; for mixed evidence use the latest direct request. Prepare a MEETING_INVITATION only when the evidence supports that the user should coordinate a discussion, or the user asks to arrange one. Resolve evidence-supported attendees with find_contacts and check availability with find_meeting_times. Show the source, responsible person if known, and each draft's recipients, channel and content or meeting details. Drafting never sends or schedules anything; require explicit final user approval before execution.",
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

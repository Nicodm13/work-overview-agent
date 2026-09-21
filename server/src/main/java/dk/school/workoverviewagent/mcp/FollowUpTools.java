package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.FollowUpItem;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FollowUpTools {

    private final IFollowUpService followUpService;
    private final IUserProvider userProvider;

    public FollowUpTools(IFollowUpService followUpService, IUserProvider userProvider) {
        this.followUpService = followUpService;
        this.userProvider = userProvider;
    }

    @McpTool(
        name = "create_follow_up_item",
        description = "Create a follow-up item. Evidence is attached explicitly in a separate call.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public FollowUpItem createFollowUpItem(
        @McpToolParam(description = "Short, user- or AI-provided title for the tracked follow-up item.", required = true)
        String title,
        @McpToolParam(description = "Optional concise summary of the follow-up item.", required = false)
        String summary) {
        return followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            userProvider.getUserId(),
            title,
            summary == null ? "" : summary,
            List.of()));
    }

    @McpTool(
        name = "get_follow_up_item",
        description = "Return one tracked follow-up item and its explicitly linked evidence references.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public FollowUpItem getFollowUpItem(
        @McpToolParam(description = "ID of the follow-up item to retrieve.", required = true)
        String followUpItemId) {
        return followUpService.getFollowUpItem(userProvider.getUserId(), followUpItemId);
    }

    @McpTool(
        name = "list_follow_up_items",
        description = "List the locally tracked follow-up items.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public List<FollowUpItem> listFollowUpItems() {
        return followUpService.listFollowUpItems(userProvider.getUserId());
    }

    @McpTool(
        name = "attach_evidence_to_follow_up",
        description = "Explicitly link one source reference to an existing follow-up item. The server does not decide whether sources are related.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public FollowUpItem attachEvidenceToFollowUp(
        @McpToolParam(description = "ID of the follow-up item that should receive the evidence link.", required = true)
        String followUpItemId,
        @McpToolParam(description = "Selected minimized source reference to link explicitly.", required = true)
        EvidenceReference evidenceReference) {
        return followUpService.attachEvidence(new AttachEvidenceToFollowUpRequest(
            userProvider.getUserId(),
            followUpItemId,
            evidenceReference));
    }
}

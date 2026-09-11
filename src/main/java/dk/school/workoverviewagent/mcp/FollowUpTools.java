package dk.school.workoverviewagent.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class FollowUpTools {

    @McpTool(
        name = "create_follow_up_item",
        description = "Create a follow-up item. Evidence is attached explicitly in a separate call.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public void createFollowUpItem() {
        // TODO: Create CreateFollowUpItemRequest and delegate to FollowUpService.
        throw new UnsupportedOperationException("create_follow_up_item is not implemented yet");
    }

    @McpTool(
        name = "get_follow_up_item",
        description = "Return one tracked follow-up item and its explicitly linked evidence references.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public void getFollowUpItem() {
        // TODO: Delegate to FollowUpService.
        throw new UnsupportedOperationException("get_follow_up_item is not implemented yet");
    }

    @McpTool(
        name = "list_follow_up_items",
        description = "List the locally tracked follow-up items.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public void listFollowUpItems() {
        // TODO: Delegate to FollowUpService.
        throw new UnsupportedOperationException("list_follow_up_items is not implemented yet");
    }

    @McpTool(
        name = "attach_evidence_to_follow_up",
        description = "Explicitly link one source reference to an existing follow-up item. The server does not decide whether sources are related.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public void attachEvidenceToFollowUp() {
        // TODO: Build EvidenceReference and AttachEvidenceToFollowUpRequest, then delegate to FollowUpService.
        throw new UnsupportedOperationException("attach_evidence_to_follow_up is not implemented yet");
    }
}

package dk.school.workoverviewagent.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class ActionTools {

    @McpTool(
            name = "draft_follow_up_action",
            description = "Prepare an editable action draft for a follow-up item. Preparing a draft does not send an external action.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false))
    public void draftFollowUpAction() {
        // TODO: Accept the full editable draft fields, create CreateActionDraftRequest, and delegate to ActionService.
        throw new UnsupportedOperationException("draft_follow_up_action is not implemented yet");
    }

    @McpTool(
            name = "approve_action_draft",
            description = "Record final user approval for the exact editable draft content before execution.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false))
    public void approveActionDraft() {
        // TODO: Create ApproveActionRequest with explicit final approval and delegate to ActionService.
        throw new UnsupportedOperationException("approve_action_draft is not implemented yet");
    }

    @McpTool(
            name = "execute_approved_action",
            description = "Execute only a previously approved draft and create an audit record. This tool must retain the explicit final-approval check.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false))
    public void executeApprovedAction() {
        // TODO: Create ExecuteApprovedActionRequest with explicit final approval and delegate to ActionService.
        throw new UnsupportedOperationException("execute_approved_action is not implemented yet");
    }
}

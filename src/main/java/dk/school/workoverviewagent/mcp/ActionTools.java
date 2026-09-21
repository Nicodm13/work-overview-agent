package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.ApproveActionResponse;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftResponse;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ActionTools {

    private final IActionService actionService;
    private final IUserProvider userProvider;

    public ActionTools(IActionService actionService, IUserProvider userProvider) {
        this.actionService = actionService;
        this.userProvider = userProvider;
    }

    @McpTool(
        name = "draft_follow_up_action",
        description = "Prepare an editable action draft for a follow-up item. Preparing a draft does not send an external action.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public CreateActionDraftResponse draftFollowUpAction(
        @McpToolParam(description = "ID of the follow-up item for which the draft is prepared.", required = true)
        String followUpItemId,
        @McpToolParam(description = "Channel and action type for the editable draft.", required = true)
        ActionType actionType,
        @McpToolParam(description = "Proposed recipients. The user can edit them before approval.", required = false)
        List<String> recipients,
        @McpToolParam(description = "Editable email subject, when applicable.", required = false)
        String subject,
        @McpToolParam(description = "Editable message or email body, when applicable.", required = false)
        String body,
        @McpToolParam(description = "Editable meeting title, when applicable.", required = false)
        String meetingTitle,
        @McpToolParam(description = "Selected meeting start as an ISO-8601 instant, when applicable.", required = false)
        String selectedStartsAt,
        @McpToolParam(description = "Selected meeting end as an ISO-8601 instant, when applicable.", required = false)
        String selectedEndsAt,
        @McpToolParam(description = "Editable meeting agenda entries, when applicable.", required = false)
        List<String> agenda,
        @McpToolParam(description = "Editable context explaining why this draft was prepared.", required = false)
        String editableContext) {
        return actionService.createDraft(new CreateActionDraftRequest(
            userProvider.getUserId(),
            actionType,
            followUpItemId,
            recipients == null ? List.of() : recipients,
            subject,
            body,
            meetingTitle,
            toInstantOrNull(selectedStartsAt),
            toInstantOrNull(selectedEndsAt),
            agenda == null ? List.of() : agenda,
            editableContext));
    }

    @McpTool(
        name = "approve_action_draft",
        description = "Record final user approval for the exact editable draft content before execution.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public ApproveActionResponse approveActionDraft(
        @McpToolParam(description = "ID of the editable draft to approve.", required = true)
        String draftId,
        @McpToolParam(description = "Must be true only after the user has given final approval.", required = true)
        boolean finalApproval,
        @McpToolParam(description = "Reference identifying the exact content the user approved.", required = true)
        String approvedContentReference) {
        return actionService.approveDraft(new ApproveActionRequest(
            userProvider.getUserId(),
            draftId,
            finalApproval,
            approvedContentReference,
            null));
    }

    @McpTool(
        name = "execute_approved_action",
        description = "Execute only a previously approved draft and create an audit record. This tool must retain the explicit final-approval check.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public ExecuteApprovedActionResponse executeApprovedAction(
        @McpToolParam(description = "ID of the approved draft to execute.", required = true)
        String draftId,
        @McpToolParam(description = "Must be true only after the user has given final approval.", required = true)
        boolean finalApproval,
        @McpToolParam(description = "Reference identifying the exact content that was approved.", required = true)
        String approvedContentReference) {
        return actionService.executeApprovedAction(new ExecuteApprovedActionRequest(
            userProvider.getUserId(),
            draftId,
            finalApproval,
            approvedContentReference,
            null));
    }

    private Instant toInstantOrNull(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}

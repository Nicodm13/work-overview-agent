package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.ApproveActionResponse;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftResponse;
import dk.school.workoverviewagent.action.contract.DeleteActionDraftResponse;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse;
import dk.school.workoverviewagent.action.contract.UpdateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.UpdateActionDraftResponse;
import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
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
        description = "Store an editable draft for an existing tracked follow-up item using client-supplied recipients and content; this tool does not compose or infer them. EMAIL needs recipients, subject, and body; TEAMS_MESSAGE needs recipients and body; MEETING_INVITATION needs recipients, meetingTitle, selectedStartsAt, and selectedEndsAt; supply its agenda too. Returns the stored draft and its ID for display and editing. Creating a draft never sends or schedules it; execution requires separate explicit final approval.",
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
        name = "update_action_draft",
        description = "Replace all editable content of an unapproved draft, preserving its ID, action type, and follow-up item. Retrieve the current draft first and supply every field to retain; omitted fields are cleared rather than preserved. Updating does not approve or execute the draft. Approved drafts cannot be edited; changed content requires a new draft and fresh final approval.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public UpdateActionDraftResponse updateActionDraft(
        @McpToolParam(description = "ID of the unapproved action draft to edit.", required = true)
        String draftId,
        @McpToolParam(description = "Edited recipients. The user can remove or replace recipients before approval.", required = false)
        List<String> recipients,
        @McpToolParam(description = "Edited email subject, when applicable.", required = false)
        String subject,
        @McpToolParam(description = "Edited message or email body, when applicable.", required = false)
        String body,
        @McpToolParam(description = "Edited meeting title, when applicable.", required = false)
        String meetingTitle,
        @McpToolParam(description = "Edited meeting start as an ISO-8601 instant, when applicable.", required = false)
        String selectedStartsAt,
        @McpToolParam(description = "Edited meeting end as an ISO-8601 instant, when applicable.", required = false)
        String selectedEndsAt,
        @McpToolParam(description = "Edited meeting agenda entries, when applicable.", required = false)
        List<String> agenda,
        @McpToolParam(description = "Edited context explaining why this draft was prepared.", required = false)
        String editableContext) {
        return actionService.updateDraft(new UpdateActionDraftRequest(
            userProvider.getUserId(),
            draftId,
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
        description = "Record explicit final user approval for the exact draft after the user has seen its current recipients and content. Returns an approval reference bound to that draft snapshot.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public ApproveActionResponse approveActionDraft(
        @McpToolParam(description = "ID of the editable draft to approve.", required = true)
        String draftId,
        @McpToolParam(description = "Must be true only after the user has given final approval.", required = true)
        boolean finalApproval) {
        return actionService.approveDraft(new ApproveActionRequest(
            userProvider.getUserId(),
            draftId,
            finalApproval,
            null));
    }

    @McpTool(
        name = "get_action_draft",
        description = "Return one stored action draft so the client can display its exact editable content.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public ActionDraft getActionDraft(
        @McpToolParam(description = "ID of the action draft to retrieve.", required = true)
        String draftId) {
        return actionService.getActionDraft(userProvider.getUserId(), draftId);
    }

    @McpTool(
        name = "list_action_drafts",
        description = "List the user's unfinished actions with their state, creation time, and state-change time. Executed actions are excluded. A failed action may have an unknown delivery outcome; consult the audit log before retrying.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public List<ActionDraft> listActionDrafts() {
        return actionService.listActiveActionDrafts(userProvider.getUserId());
    }

    @McpTool(
        name = "get_action_audit_log",
        description = "Return the signed-in user's action execution audit entries, including delivery outcomes. This tool cannot modify audit entries.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public List<AuditLogEntry> getActionAuditLog() {
        return actionService.auditLog(userProvider.getUserId());
    }

    @McpTool(
        name = "delete_action_draft",
        description = "Delete an unapproved draft only when the user requests its deletion. An old or superseded draft is not automatically disposable. Approved or executed drafts cannot be deleted because they support auditability.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = true,
            idempotentHint = false))
    public DeleteActionDraftResponse deleteActionDraft(
        @McpToolParam(description = "ID of the unapproved action draft to delete.", required = true)
        String draftId) {
        actionService.deleteDraft(userProvider.getUserId(), draftId);
        return new DeleteActionDraftResponse(draftId, true);
    }

    @McpTool(
        name = "execute_approved_action",
        description = "Execute a previously approved, unchanged draft and record its outcome. Mock mode simulates delivery; Graph mode sends a Teams message or email, or creates a meeting invitation. Outcomes distinguish Graph acceptance, simulation, failure, and unknown delivery.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = true,
            idempotentHint = false))
    public ExecuteApprovedActionResponse executeApprovedAction(
        @McpToolParam(description = "ID of the approved draft to execute.", required = true)
        String draftId,
        @McpToolParam(description = "Server-generated approval reference returned by approve_action_draft.", required = true)
        String approvalReference) {
        return actionService.executeApprovedAction(new ExecuteApprovedActionRequest(
            userProvider.getUserId(),
            draftId,
            approvalReference));
    }

    private Instant toInstantOrNull(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}

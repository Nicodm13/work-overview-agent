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
        description = "Prepare and store an editable action draft for an actionable finding that needs information, a response, coordination, or notification from other people. During every work review, choose and create a suitable draft without waiting for the user to ask. Match the channel to the source: EMAIL for an email-based request, TEAMS_MESSAGE for a Teams-based request; for mixed sources use the latest direct request. For joint clarification or a shared decision, first resolve evidence-supported participants with find_contacts and check availability with find_meeting_times, then draft a MEETING_INVITATION with a useful title, selected time, attendees, and agenda. If the owner is unknown, draft to known, evidence-supported stakeholders asking who owns it; do not invent recipients or omit a draft because the owner is unknown. Include source context in the draft. Preparing a draft never sends or schedules anything and is not user approval. Meeting attendees receive an invitation only after explicit approved execution.",
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
        description = "Replace the editable content of an unapproved action draft. Updating a draft does not approve or send it.",
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
        description = "Record final user approval for the exact editable draft content before execution. Call only after the user has seen the current recipients and content and explicitly approved them. Returns a server-generated reference bound to that exact draft snapshot.",
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
        description = "List active action drafts. Executed drafts remain available through audit storage but are excluded.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public List<ActionDraft> listActionDrafts() {
        return actionService.listActiveActionDrafts(userProvider.getUserId());
    }

    @McpTool(
        name = "delete_action_draft",
        description = "Delete an unapproved draft. Approved or executed drafts cannot be deleted because they support "
            + "auditability.",
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
        description = "Deliver only a previously approved, unchanged draft through the configured source provider and record the outcome. In mock mode this simulates delivery; in Graph mode it sends a Teams chat message or email, or creates a calendar event and invites its attendees. Outcomes distinguish Graph acceptance, simulation, failure, and unknown network results.",
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

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
        description = "Prepare and store an editable action draft for a finding after assessing the source evidence and who is responsible. Draft appropriate user actions proactively without waiting for the user to ask. A participant is not necessarily the person responsible for the next step: check who was addressed or agreed to act. If someone else is responsible, do not make the signed-in user take over. When the user is waiting on that clearly identified person and an update would be useful, draft a courteous message asking for an update, without assuming the work is overdue or incomplete. If responsibility is unclear, say so and suggest checking with an evidence-supported person when useful. Match the channel to the source: EMAIL for an email-based request, TEAMS_MESSAGE for a Teams-based request; for mixed sources use the latest direct request. Draft a MEETING_INVITATION only when evidence supports that the user should coordinate it or the user asks to arrange one. Resolve evidence-supported participants with find_contacts and check availability with find_meeting_times before drafting. Do not invent recipients. Include source context in the draft. Preparing a draft never sends or schedules anything and is not user approval. Meeting attendees receive an invitation only after explicit approved execution.",
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
        description = "Replace the editable content of an unapproved action draft. Updating a draft does not approve or send it. Before concluding an action workflow, use list_action_drafts and mention all remaining active drafts, including drafts outside the one just updated. Identify outdated or superseded drafts when the conversation provides that context; do not delete or execute them without authorization.",
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
        description = "Record final user approval for the exact editable draft content before execution. Call only after the user has seen the current recipients and content and explicitly approved them. Returns a server-generated reference bound to that exact draft snapshot. Before concluding an action workflow, use list_action_drafts and mention all remaining active drafts; do not imply that approving one draft approves or executes any others.",
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
        description = "List the user's unfinished actions with their current state (DRAFT, APPROVED, EXECUTING, or FAILED), creation time, and state-change time. Executed actions remain available through audit storage but are excluded. Use this before concluding a review or action workflow and mention all unfinished actions with enough context to identify them and their state. Distinguish actions created in the current interaction from older pending actions; do not assume old actions are obsolete. Identify outdated or superseded drafts when known, and ask the user what to do with them. FAILED can include an unknown delivery outcome: consult the audit log and do not claim it was not sent or retry automatically. Do not delete, approve, or execute drafts without the necessary user authorization.",
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
        description = "Deliver only a previously approved, unchanged draft through the configured source provider and record the outcome. In mock mode this simulates delivery; in Graph mode it sends a Teams chat message or email, or creates a calendar event and invites its attendees. Outcomes distinguish Graph acceptance, simulation, failure, and unknown network results. After reporting the outcome and before concluding the workflow, call list_action_drafts and mention all remaining active drafts; do not imply that executing one draft executed any others. Flag outdated or superseded drafts when known without deleting them automatically.",
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

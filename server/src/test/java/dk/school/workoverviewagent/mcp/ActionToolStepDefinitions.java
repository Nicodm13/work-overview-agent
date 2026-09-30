package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.action.contract.ApproveActionResponse;
import dk.school.workoverviewagent.action.contract.CreateActionDraftResponse;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse;
import dk.school.workoverviewagent.action.contract.UpdateActionDraftResponse;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.FollowUpItem;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class ActionToolStepDefinitions {

    @Autowired
    private FollowUpTools followUpTools;
    @Autowired
    private ActionTools actionTools;

    private FollowUpItem createdFollowUpItem;
    private CreateActionDraftResponse actionDraftResponse;
    private ApproveActionResponse actionApprovalResponse;
    private ExecuteApprovedActionResponse actionExecutionResponse;
    private UpdateActionDraftResponse updatedActionDraftResponse;
    private Throwable failure;

    @When("the action tool scenario creates a follow-up item with title {string}")
    public void createFollowUpItem(String title) {
        createdFollowUpItem = followUpTools.createFollowUpItem(title, "Created through the MCP tool.");
    }

    @When("the draft_follow_up_action MCP tool creates a Teams message draft")
    public void draftFollowUpAction() {
        actionDraftResponse = actionTools.draftFollowUpAction(
            createdFollowUpItem.id(),
            ActionType.TEAMS_MESSAGE,
            List.of("Maja Jensen"),
            null,
            "Could you share a status update?",
            null,
            null,
            null,
            List.of(),
            "Explicitly requested by the user.");
    }

    @When("the draft_follow_up_action MCP tool creates a meeting invitation draft")
    public void draftMeetingInvitation() {
        actionDraftResponse = actionTools.draftFollowUpAction(
            createdFollowUpItem.id(),
            ActionType.MEETING_INVITATION,
            List.of("Maja Jensen"),
            null,
            null,
            "Integration clarification",
            "2026-10-01T10:00:00Z",
            "2026-10-01T10:30:00Z",
            List.of("Review the open question"),
            "Prepared from the selected follow-up item.");
    }

    @Then("the action draft references the created follow-up item")
    public void actionDraftReferencesCreatedFollowUpItem() {
        assertThat(actionDraftResponse.draft().followUpItemId()).isEqualTo(createdFollowUpItem.id());
    }

    @Then("the meeting invitation draft contains the proposed details")
    public void meetingInvitationDraftContainsProposedDetails() {
        assertThat(actionDraftResponse.draft().actionType()).isEqualTo(ActionType.MEETING_INVITATION);
        assertThat(actionDraftResponse.draft().recipients()).containsExactly("Maja Jensen");
        assertThat(actionDraftResponse.draft().meetingTitle()).isEqualTo("Integration clarification");
        assertThat(actionDraftResponse.draft().selectedStartsAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(actionDraftResponse.draft().selectedEndsAt()).isEqualTo(Instant.parse("2026-10-01T10:30:00Z"));
        assertThat(actionDraftResponse.draft().agenda()).containsExactly("Review the open question");
    }

    @When("the update_action_draft MCP tool edits the meeting invitation draft")
    public void updateMeetingInvitationDraft() {
        updatedActionDraftResponse = actionTools.updateActionDraft(
            actionDraftResponse.draft().id(),
            List.of("Maja Jensen", "Thomas Hansen"),
            null,
            null,
            "Integration decision",
            "2026-10-01T09:00:00Z",
            "2026-10-01T09:45:00Z",
            List.of("Decide next steps", "Assign owners"),
            "Updated by the user before approval.");
    }

    @Then("the meeting invitation draft contains the edited details")
    public void meetingInvitationDraftContainsEditedDetails() {
        assertThat(updatedActionDraftResponse.draft().recipients()).containsExactly("Maja Jensen", "Thomas Hansen");
        assertThat(updatedActionDraftResponse.draft().meetingTitle()).isEqualTo("Integration decision");
        assertThat(updatedActionDraftResponse.draft().selectedStartsAt()).isEqualTo(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(updatedActionDraftResponse.draft().selectedEndsAt()).isEqualTo(Instant.parse("2026-10-01T09:45:00Z"));
        assertThat(updatedActionDraftResponse.draft().agenda()).containsExactly("Decide next steps", "Assign owners");
    }

    @When("the approve_action_draft MCP tool records final approval")
    public void approveActionDraft() {
        actionApprovalResponse = actionTools.approveActionDraft(
            actionDraftResponse.draft().id(),
            true,
            "mcp-tool-content-v1");
    }

    @When("the update_action_draft MCP tool attempts to edit the approved draft")
    public void updateApprovedDraft() {
        try {
            actionTools.updateActionDraft(
                actionDraftResponse.draft().id(),
                List.of("Maja Jensen"),
                null,
                null,
                "Changed after approval",
                "2026-10-01T11:00:00Z",
                "2026-10-01T11:30:00Z",
                List.of("This must not be saved"),
                "This must not be saved.");
        } catch (Throwable throwable) {
            failure = throwable;
        }
    }

    @Then("the action draft is approved")
    public void actionDraftIsApproved() {
        assertThat(actionApprovalResponse.approved()).isTrue();
    }

    @Then("the approved action draft cannot be edited")
    public void approvedActionDraftCannotBeEdited() {
        assertThat(failure)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("only unapproved drafts can be edited");
    }

    @When("the execute_approved_action MCP tool executes the approved draft")
    public void executeApprovedAction() {
        actionExecutionResponse = actionTools.executeApprovedAction(
            actionDraftResponse.draft().id(),
            true,
            "mcp-tool-content-v1");
    }

    @Then("the action execution is audited as approved")
    public void actionExecutionIsAuditedAsApproved() {
        assertThat(actionExecutionResponse.auditLogEntry().followUpItemId())
            .isEqualTo(createdFollowUpItem.id());
        assertThat(actionExecutionResponse.auditLogEntry().approvalStatus()).isEqualTo("APPROVED");
    }
}

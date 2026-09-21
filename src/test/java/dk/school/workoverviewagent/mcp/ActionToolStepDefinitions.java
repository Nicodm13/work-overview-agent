package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.action.contract.ApproveActionResponse;
import dk.school.workoverviewagent.action.contract.CreateActionDraftResponse;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.FollowUpItem;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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

    @Then("the action draft references the created follow-up item")
    public void actionDraftReferencesCreatedFollowUpItem() {
        assertThat(actionDraftResponse.draft().followUpItemId()).isEqualTo(createdFollowUpItem.id());
    }

    @When("the approve_action_draft MCP tool records final approval")
    public void approveActionDraft() {
        actionApprovalResponse = actionTools.approveActionDraft(
            actionDraftResponse.draft().id(),
            true,
            "mcp-tool-content-v1");
    }

    @Then("the action draft is approved")
    public void actionDraftIsApproved() {
        assertThat(actionApprovalResponse.approved()).isTrue();
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

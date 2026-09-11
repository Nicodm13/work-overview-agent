package dk.school.workoverviewagent.services;

import static org.assertj.core.api.Assertions.assertThat;
import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class ActionStepDefinitions {
    @Autowired private IActionService actionService;
    @Autowired private IFollowUpService followUpService;
    private dk.school.workoverviewagent.action.contract.CreateActionDraftResponse draft;
    private dk.school.workoverviewagent.action.contract.ExecuteApprovedActionResponse execution;
    private Throwable failure;

    @Given("a Teams action draft for follow-up item {string}")
    public void teamsActionDraft(String title) {
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(title, "", List.of()));
        draft = actionService.createDraft(new CreateActionDraftRequest(ActionType.TEAMS_MESSAGE, item.id(), List.of("Maja Jensen"), null, "Could you share a status update?", null, null, null, List.of(), "Explicit context."));
    }

    @When("the action is executed without final approval")
    public void executeWithoutApproval() {
        try {
            actionService.executeApprovedAction(new ExecuteApprovedActionRequest(draft.draft().id(), false, "content-1", Instant.parse("2026-09-07T10:00:00Z")));
        } catch (Throwable throwable) {
            failure = throwable;
        }
    }

    @When("the action is approved and executed")
    public void approveAndExecute() {
        actionService.approveDraft(new ApproveActionRequest(draft.draft().id(), true, "content-1", Instant.parse("2026-09-07T09:59:00Z")));
        execution = actionService.executeApprovedAction(new ExecuteApprovedActionRequest(draft.draft().id(), true, "content-1", Instant.parse("2026-09-07T10:00:00Z")));
    }

    @Then("the action execution is rejected")
    public void actionExecutionRejected() {
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessage("finalApproval must be true");
    }

    @Then("the action is audited as approved for follow-up item {string}")
    public void actionIsAudited(String ignoredTitle) {
        assertThat(execution.auditLogEntry().followUpItemId()).isEqualTo(draft.draft().followUpItemId());
        assertThat(execution.auditLogEntry().approvalStatus()).isEqualTo("APPROVED");
    }
}

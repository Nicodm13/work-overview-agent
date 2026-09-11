package dk.school.workoverviewagent.services;

import static org.assertj.core.api.Assertions.assertThat;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.status.api.IStatusService;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;

public class StatusStepDefinitions {
    @Autowired private IStatusService statusService;
    @Autowired private IFollowUpService followUpService;
    private WorkStatus currentStatus;
    private StatusSource currentSource;

    @Given("no user-confirmed status exists for follow-up item {string}")
    public void noUserStatus(String title) {
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest("user-1", title, "", java.util.List.of()));
        var response = statusService.getWorkStatus(new GetWorkStatusRequest("user-1", item.id()));
        currentStatus = response.workStatus();
        currentSource = response.statusSource();
    }

    @When("the user marks follow-up item {string} as {string} with reason {string}")
    public void userMarksStatus(String title, String status, String reason) {
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest("user-1", title, "", java.util.List.of()));
        var response = statusService.updateWorkStatus(new UpdateWorkStatusRequest("user-1", item.id(), WorkStatus.valueOf(status), reason, StatusSource.USER_CONFIRMED, Instant.parse("2026-09-05T08:30:00Z")));
        currentStatus = response.workStatus();
        currentSource = response.statusSource();
    }

    @Then("the work status is {string} and the status source is {string}")
    public void workStatusIs(String status, String source) {
        assertThat(currentStatus).isEqualTo(WorkStatus.valueOf(status));
        assertThat(currentSource).isEqualTo(StatusSource.valueOf(source));
    }
}

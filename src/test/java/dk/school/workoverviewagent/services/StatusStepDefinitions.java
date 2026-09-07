package dk.school.workoverviewagent.services;

import static org.assertj.core.api.Assertions.assertThat;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
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
    private WorkStatus currentStatus;
    private StatusSource currentSource;

    @Given("no user-confirmed status exists for evidence {string}")
    public void noUserStatus(String evidenceId) {
        var response = statusService.getWorkStatus(new GetWorkStatusRequest(evidenceId));
        currentStatus = response.workStatus();
        currentSource = response.statusSource();
    }

    @When("the user marks evidence {string} as {string} with reason {string}")
    public void userMarksStatus(String evidenceId, String status, String reason) {
        var response = statusService.updateWorkStatus(new UpdateWorkStatusRequest(evidenceId, WorkStatus.valueOf(status), reason, StatusSource.USER_CONFIRMED, Instant.parse("2026-09-05T08:30:00Z")));
        currentStatus = response.workStatus();
        currentSource = response.statusSource();
    }

    @Then("the work status is {string} and the status source is {string}")
    public void workStatusIs(String status, String source) {
        assertThat(currentStatus).isEqualTo(WorkStatus.valueOf(status));
        assertThat(currentSource).isEqualTo(StatusSource.valueOf(source));
    }
}

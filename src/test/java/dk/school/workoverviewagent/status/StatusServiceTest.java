package dk.school.workoverviewagent.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusServiceTest {

    private final StatusService statusService = new StatusService();

    @Test
    void shouldReturnUnverifiedDigitalEvidenceStatusWhenNoUserStatusExists() {
        var status = statusService.getWorkStatus(new GetWorkStatusRequest("evidence-1"));

        assertThat(status.evidenceId()).isEqualTo("evidence-1");
        assertThat(status.workStatus()).isEqualTo(WorkStatus.UNVERIFIED);
        assertThat(status.statusSource()).isEqualTo(StatusSource.DIGITAL_EVIDENCE);
        assertThat(status.reason()).isEqualTo("No user-confirmed status recorded.");
        assertThat(status.updatedAt()).isNull();
        assertThat(status.history()).isEmpty();
    }

    @Test
    void shouldUpdateUserConfirmedStatusAndPreserveHistory() {
        statusService.updateWorkStatus(new UpdateWorkStatusRequest(
                "evidence-1",
                WorkStatus.WAITING,
                "Waiting for Maja to confirm environment readiness.",
                StatusSource.USER_CONFIRMED,
                Instant.parse("2026-09-04T10:00:00Z")));

        var response = statusService.updateWorkStatus(new UpdateWorkStatusRequest(
                "evidence-1",
                WorkStatus.RESOLVED,
                "Confirmed in standup.",
                StatusSource.USER_CONFIRMED,
                Instant.parse("2026-09-05T08:30:00Z")));

        assertThat(response.workStatus()).isEqualTo(WorkStatus.RESOLVED);
        assertThat(response.statusSource()).isEqualTo(StatusSource.USER_CONFIRMED);
        assertThat(response.reason()).isEqualTo("Confirmed in standup.");
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-09-05T08:30:00Z"));
        assertThat(response.history())
                .extracting("status")
                .containsExactly(WorkStatus.WAITING, WorkStatus.RESOLVED);
    }

    @Test
    void shouldApplyStoredUserConfirmedStatusToEvidenceItems() {
        statusService.updateWorkStatus(new UpdateWorkStatusRequest(
                "evidence-1",
                WorkStatus.OPEN,
                "I need to answer this.",
                StatusSource.USER_CONFIRMED,
                Instant.parse("2026-09-04T10:00:00Z")));

        var statusItems = statusService.applyCurrentStatus(List.of(evidenceItem("evidence-1")));

        assertThat(statusItems).singleElement().satisfies(statusItem -> {
            assertThat(statusItem.workStatus()).isEqualTo(WorkStatus.OPEN);
            assertThat(statusItem.statusSource()).isEqualTo(StatusSource.USER_CONFIRMED);
            assertThat(statusItem.history()).hasSize(1);
        });
    }

    @Test
    void shouldNotAutomaticallyReopenResolvedItemsFromDigitalEvidence() {
        statusService.updateWorkStatus(new UpdateWorkStatusRequest(
                "evidence-1",
                WorkStatus.RESOLVED,
                "Already handled.",
                StatusSource.USER_CONFIRMED,
                Instant.parse("2026-09-04T10:00:00Z")));

        var statusItems = statusService.applyCurrentStatus(List.of(evidenceItem("evidence-1")));

        assertThat(statusItems).singleElement().satisfies(statusItem -> {
            assertThat(statusItem.workStatus()).isEqualTo(WorkStatus.RESOLVED);
            assertThat(statusItem.statusSource()).isEqualTo(StatusSource.USER_CONFIRMED);
        });
    }

    @Test
    void shouldRejectDigitalEvidenceAsAStatusUpdateSource() {
        var request = new UpdateWorkStatusRequest(
                "evidence-1",
                WorkStatus.POSSIBLY_REOPENED,
                "New source data might conflict.",
                StatusSource.DIGITAL_EVIDENCE,
                Instant.parse("2026-09-04T10:00:00Z"));

        assertThatThrownBy(() -> statusService.updateWorkStatus(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("work status updates must be user-confirmed");
    }

    private EvidenceItem evidenceItem(String id) {
        return new EvidenceItem(
                id,
                "Decision needed: integration approach",
                "Source evidence captured from OUTLOOK.",
                EvidenceStatus.SOURCE_EVIDENCE_CAPTURED,
                List.of());
    }
}

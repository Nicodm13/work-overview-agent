package dk.school.workoverviewagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.status.api.IStatusService;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PersistenceIntegrationTest extends CucumberSpringConfiguration {

    private static final String OWNER_ID = "persistence-test-user";

    @Autowired
    private IFollowUpService followUpService;

    @Autowired
    private IEvidenceService evidenceService;

    @Autowired
    private IStatusService statusService;

    @Autowired
    private IActionService actionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsIndependentEvidenceAndFollowUpLinks() {
        var reference = evidenceReference("evidence-reference-shared");
        var first = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID,
            "First follow-up",
            "First explicit link.",
            List.of(reference)));
        var second = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID,
            "Second follow-up",
            "Second explicit link.",
            List.of()));

        followUpService.attachEvidence(new AttachEvidenceToFollowUpRequest(
            OWNER_ID,
            second.id(),
            reference));

        assertThat(followUpService.getFollowUpItem(OWNER_ID, first.id()).evidenceReferences())
            .containsExactly(reference);
        assertThat(followUpService.getFollowUpItem(OWNER_ID, second.id()).evidenceReferences())
            .containsExactly(reference);
        assertThat(evidenceService.getEvidence(OWNER_ID, reference.id()).references())
            .containsExactly(reference);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM EVIDENCE_REFERENCE WHERE OWNER_ID = ?",
            Integer.class,
            OWNER_ID)).isEqualTo(1);
    }

    @Test
    void reloadsStatusHistoryAndAuditEntriesInDeterministicOrder() {
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID,
            "Persisted work item",
            "",
            List.of()));
        var firstUpdatedAt = Instant.parse("2026-09-10T09:00:00Z");
        var secondUpdatedAt = Instant.parse("2026-09-10T10:00:00Z");

        statusService.updateWorkStatus(new UpdateWorkStatusRequest(
            OWNER_ID,
            item.id(),
            WorkStatus.OPEN,
            "User confirmed it is open.",
            StatusSource.USER_CONFIRMED,
            firstUpdatedAt));
        statusService.updateWorkStatus(new UpdateWorkStatusRequest(
            OWNER_ID,
            item.id(),
            WorkStatus.WAITING,
            "Waiting for a response.",
            StatusSource.USER_CONFIRMED,
            secondUpdatedAt));

        var history = statusService.getWorkStatus(new GetWorkStatusRequest(OWNER_ID, item.id()));
        assertThat(history.history()).extracting(record -> record.updatedAt())
            .containsExactly(firstUpdatedAt, secondUpdatedAt);
        assertThat(history.workStatus()).isEqualTo(WorkStatus.WAITING);

        var draft = actionService.createDraft(new CreateActionDraftRequest(
            OWNER_ID,
            ActionType.EMAIL,
            item.id(),
            List.of("maja@example.test"),
            "Status update",
            "Could you provide a status update?",
            null,
            null,
            null,
            List.of(),
            "Explicitly requested by the user.")).draft();
        var contentReference = "draft-content-v1";
        var approvedAt = Instant.parse("2026-09-10T10:30:00Z");
        var executedAt = Instant.parse("2026-09-10T10:31:00Z");

        actionService.approveDraft(new ApproveActionRequest(
            OWNER_ID,
            draft.id(),
            true,
            contentReference,
            approvedAt));
        actionService.executeApprovedAction(new ExecuteApprovedActionRequest(
            OWNER_ID,
            draft.id(),
            true,
            contentReference,
            executedAt));

        assertThat(actionService.auditLog(OWNER_ID)).singleElement().satisfies(entry -> {
            assertThat(entry.followUpItemId()).isEqualTo(item.id());
            assertThat(entry.timestamp()).isEqualTo(executedAt);
            assertThat(entry.approvalStatus()).isEqualTo("APPROVED");
        });
    }

    private EvidenceReference evidenceReference(String id) {
        return new EvidenceReference(
            id,
            SourceType.TEAMS,
            "teams-message-1",
            Instant.parse("2026-09-10T08:00:00Z"),
            "Maja Jensen",
            "Test environment clarification",
            "Can you confirm whether the test environment is ready?",
            1.0);
    }
}

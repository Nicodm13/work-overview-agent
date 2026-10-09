package dk.school.workoverviewagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.action.contract.ApproveActionRequest;
import dk.school.workoverviewagent.action.contract.CreateActionDraftRequest;
import dk.school.workoverviewagent.action.contract.ExecuteApprovedActionRequest;
import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.evidence.repository.IEvidenceRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

class PersistenceIntegrationTest extends CucumberSpringConfiguration {

    private static final String OWNER_ID = "persistence-test-user";

    @Autowired
    private IFollowUpService followUpService;

    @Autowired
    private IEvidenceService evidenceService;

    @Autowired
    private IEvidenceRepository evidenceRepository;

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
            reference.id()));

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
    void rejectsUnknownEvidenceInsteadOfCreatingItDuringAttachment() {
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID, "Unknown evidence", "", List.of()));
        var unknown = evidenceReference("evidence-reference-unknown");

        assertThatThrownBy(() -> followUpService.attachEvidence(new AttachEvidenceToFollowUpRequest(
            OWNER_ID, item.id(), unknown.id())))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(evidenceService.getEvidence(OWNER_ID, unknown.id()).references()).isEmpty();
        assertThat(followUpService.getFollowUpItem(OWNER_ID, item.id()).evidenceReferences()).isEmpty();
    }

    @Test
    void rejectsEvidenceOwnedByAnotherUser() {
        var otherOwner = "other-persistence-test-user";
        var foreign = evidenceReference("evidence-reference-foreign");
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            otherOwner, "Other user's follow-up", "", List.of(foreign)));
        var ownItem = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID, "Own follow-up", "", List.of()));

        assertThatThrownBy(() -> followUpService.attachEvidence(new AttachEvidenceToFollowUpRequest(
            OWNER_ID, ownItem.id(), foreign.id())))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(followUpService.getFollowUpItem(otherOwner, item.id()).evidenceReferences())
            .containsExactly(foreign);
        assertThat(followUpService.getFollowUpItem(OWNER_ID, ownItem.id()).evidenceReferences()).isEmpty();
    }

    @Test
    void crossOwnerEvidenceUpsertCannotChangeEvidenceOrVersionHistory() {
        var owner = "evidence-upsert-owner";
        var otherOwner = "evidence-upsert-other-owner";
        var original = evidenceReference("evidence-reference-cross-owner-upsert");
        evidenceRepository.save(owner, original);
        var changed = new EvidenceReference(
            original.id(), original.sourceType(), original.sourceId(), original.timestamp(),
            original.author(), original.title(), "Other owner's excerpt", original.confidence());

        evidenceRepository.save(otherOwner, changed);

        assertThat(evidenceService.getEvidence(owner, original.id()).references()).containsExactly(original);
        assertThat(evidenceService.getEvidence(otherOwner, original.id()).references()).isEmpty();
        assertThat(jdbc.queryForObject(
            "SELECT VERSION FROM EVIDENCE_REFERENCE WHERE ID = ?", Integer.class, original.id())).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM EVIDENCE_REFERENCE_VERSION WHERE EVIDENCE_REFERENCE_ID = ?",
            Integer.class, original.id())).isEqualTo(1);
    }

    @Test
    void databaseRejectsCrossOwnerRelationships() {
        var owner = "relationship-owner";
        var otherOwner = "relationship-other-owner";
        var reference = evidenceReference("evidence-reference-cross-owner-relationship");
        var foreignReference = evidenceReference("evidence-reference-foreign-relationship");
        var item = followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            owner, "Owner's item", "", List.of(reference)));
        evidenceRepository.save(otherOwner, foreignReference);
        var draftId = "cross-owner-relationship-draft";

        jdbc.update("""
            INSERT INTO WORK_STATUS_RECORD
                (ID, OWNER_ID, FOLLOW_UP_ITEM_ID, STATUS, REASON, STATUS_SOURCE,
                 UPDATED_AT, CREATED, CREATED_BY, CHANGED, CHANGED_BY, VERSION)
            VALUES (?, ?, ?, 'OPEN', '', 'USER_CONFIRMED', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, 0)
            """, "cross-owner-relationship-status", owner, item.id(), owner, owner);
        jdbc.update("""
            INSERT INTO ACTION_DRAFT
                (ID, OWNER_ID, FOLLOW_UP_ITEM_ID, ACTION_TYPE, RECIPIENTS, AGENDA,
                 CREATED, CREATED_BY, CHANGED, CHANGED_BY, VERSION)
            VALUES (?, ?, ?, 'EMAIL', '', '', CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, 0)
            """, draftId, owner, item.id(), owner, owner);
        jdbc.update("""
            INSERT INTO ACTION_STATE
                (OWNER_ID, DRAFT_ID, STATUS, UPDATED_AT)
            VALUES (?, ?, 'DRAFT', CURRENT_TIMESTAMP)
            """, owner, draftId);
        jdbc.update("""
            INSERT INTO AUDIT_LOG_ENTRY
                (ID, OWNER_ID, FOLLOW_UP_ITEM_ID, ACTION_TYPE, OCCURRED_AT,
                 APPROVAL_STATUS, APPROVED_CONTENT_REFERENCE,
                 CREATED, CREATED_BY, CHANGED, CHANGED_BY, VERSION)
            VALUES (?, ?, ?, 'EMAIL', CURRENT_TIMESTAMP, 'APPROVED', 'content',
                    CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, 0)
            """, "cross-owner-relationship-audit", owner, item.id(), owner, owner);

        assertCrossOwnerUpdateRejected("FOLLOW_UP_EVIDENCE_REFERENCE", "FOLLOW_UP_ITEM_ID", item.id(), otherOwner);
        assertThatThrownBy(() -> jdbc.update("""
            UPDATE FOLLOW_UP_EVIDENCE_REFERENCE
            SET EVIDENCE_REFERENCE_ID = ?
            WHERE OWNER_ID = ? AND FOLLOW_UP_ITEM_ID = ? AND EVIDENCE_REFERENCE_ID = ?
            """, foreignReference.id(), owner, item.id(), reference.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertCrossOwnerUpdateRejected("WORK_STATUS_RECORD", "ID", "cross-owner-relationship-status", otherOwner);
        assertCrossOwnerUpdateRejected("ACTION_DRAFT", "ID", draftId, otherOwner);
        assertCrossOwnerUpdateRejected("ACTION_STATE", "DRAFT_ID", draftId, otherOwner);
        assertCrossOwnerUpdateRejected("AUDIT_LOG_ENTRY", "ID", "cross-owner-relationship-audit", otherOwner);
        assertCrossOwnerUpdateRejected("EVIDENCE_REFERENCE_VERSION", "EVIDENCE_REFERENCE_ID", reference.id(), otherOwner);
    }

    private void assertCrossOwnerUpdateRejected(
        String table, String idColumn, String id, String otherOwner) {
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE " + table + " SET OWNER_ID = ? WHERE " + idColumn + " = ?",
            otherOwner, id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatesCapturedEvidenceWhenTheSourceReferenceChanges() {
        var original = evidenceReference("evidence-reference-updated");
        var updated = new EvidenceReference(
            original.id(),
            original.sourceType(),
            original.sourceId(),
            original.timestamp(),
            original.author(),
            original.title(),
            "Updated source excerpt.",
            original.confidence());

        followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID,
            "Evidence update",
            "",
            List.of(original)));
        followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID,
            "Evidence update after source edit",
            "",
            List.of(updated)));

        assertThat(evidenceService.getEvidence(OWNER_ID, original.id()).references())
            .containsExactly(updated);
        assertThat(jdbc.queryForObject(
            "SELECT VERSION FROM EVIDENCE_REFERENCE WHERE ID = ?",
            Integer.class,
            original.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM EVIDENCE_REFERENCE_VERSION WHERE EVIDENCE_REFERENCE_ID = ?",
            Integer.class,
            original.id())).isEqualTo(2);
    }

    @Test
    void rollsBackFollowUpCreationWhenEvidenceCannotBePersisted() {
        var countBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM FOLLOW_UP_ITEM WHERE OWNER_ID = ?",
            Integer.class,
            OWNER_ID);
        var invalidReference = new EvidenceReference(
            "invalid-evidence-reference",
            SourceType.TEAMS,
            "teams-message-invalid",
            Instant.parse("2026-09-10T08:00:00Z"),
            "Maja Jensen",
            null,
            "Invalid source evidence.",
            1.0);

        assertThatThrownBy(() -> followUpService.createFollowUpItem(new CreateFollowUpItemRequest(
            OWNER_ID,
            "Follow-up that must roll back",
            "",
            List.of(invalidReference))))
            .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM FOLLOW_UP_ITEM WHERE OWNER_ID = ?",
            Integer.class,
            OWNER_ID)).isEqualTo(countBefore);
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

        assertThatThrownBy(() -> actionService.executeApprovedAction(
            new ExecuteApprovedActionRequest(
                OWNER_ID,
                draft.id(),
                true,
                contentReference,
                executedAt)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(actionService.auditLog(OWNER_ID)).singleElement().satisfies(entry -> {
            assertThat(entry.followUpItemId()).isEqualTo(item.id());
            assertThat(entry.timestamp()).isEqualTo(executedAt);
            assertThat(entry.approvalStatus()).isEqualTo("APPROVED");
        });
        assertThat(jdbc.queryForObject(
            "SELECT STATUS FROM ACTION_STATE WHERE OWNER_ID = ? AND DRAFT_ID = ?",
            String.class,
            OWNER_ID,
            draft.id())).isEqualTo("EXECUTED");
    }

    private EvidenceReference evidenceReference(String id) {
        return new EvidenceReference(
            id,
            SourceType.TEAMS,
            "teams-message-" + id,
            Instant.parse("2026-09-10T08:00:00Z"),
            "Maja Jensen",
            "Test environment clarification",
            "Can you confirm whether the test environment is ready?",
            1.0);
    }
}

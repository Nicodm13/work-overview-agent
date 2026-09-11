package dk.school.workoverviewagent.action.repository;

import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionState;
import dk.school.workoverviewagent.model.ActionStatus;
import dk.school.workoverviewagent.model.ActionType;
import dk.school.workoverviewagent.model.AuditLogEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ActionRepository implements IActionRepository {

    private static final String LIST_SEPARATOR = "\u001F";

    private final JdbcTemplate jdbc;

    ActionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveDraft(String ownerId, ActionDraft draft) {
        jdbc.update(
            """
                INSERT INTO ACTION_DRAFT
                    (ID, OWNER_ID, FOLLOW_UP_ITEM_ID, ACTION_TYPE, RECIPIENTS,
                     SUBJECT, BODY, MEETING_TITLE, SELECTED_STARTS_AT,
                     SELECTED_ENDS_AT, AGENDA, EDITABLE_CONTEXT, CREATED,
                     CREATED_BY, CHANGED, CHANGED_BY, VERSION)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                        ?, CURRENT_TIMESTAMP, ?, 0)
                """,
            draft.id(),
            ownerId,
            draft.followUpItemId(),
            draft.actionType().name(),
            serialize(draft.recipients()),
            draft.subject(),
            draft.body(),
            draft.meetingTitle(),
            timestamp(draft.selectedStartsAt()),
            timestamp(draft.selectedEndsAt()),
            serialize(draft.agenda()),
            draft.editableContext(),
            ownerId,
            ownerId);
    }

    @Override
    public Optional<ActionDraft> findDraftById(String ownerId, String draftId) {
        return jdbc.query(
                """
                    SELECT ID, OWNER_ID, FOLLOW_UP_ITEM_ID, ACTION_TYPE, RECIPIENTS,
                           SUBJECT, BODY, MEETING_TITLE, SELECTED_STARTS_AT,
                           SELECTED_ENDS_AT, AGENDA, EDITABLE_CONTEXT
                    FROM ACTION_DRAFT
                    WHERE OWNER_ID = ? AND ID = ?
                    """,
                (resultSet, rowNumber) -> draft(resultSet),
                ownerId,
                draftId)
            .stream()
            .findFirst();
    }

    @Override
    public void saveActionState(ActionState state) {
        jdbc.update(
            """
                INSERT INTO ACTION_STATE
                    (OWNER_ID, DRAFT_ID, STATUS, APPROVED_CONTENT_REFERENCE, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (OWNER_ID, DRAFT_ID)
                DO UPDATE SET STATUS = EXCLUDED.STATUS,
                              APPROVED_CONTENT_REFERENCE = EXCLUDED.APPROVED_CONTENT_REFERENCE,
                              UPDATED_AT = EXCLUDED.UPDATED_AT
                """,
            state.ownerId(),
            state.draftId(),
            state.status().name(),
            state.approvedContentReference(),
            Timestamp.from(state.updatedAt()));
    }

    @Override
    public Optional<ActionState> findActionState(String ownerId, String draftId) {
        return jdbc.query(
                """
                    SELECT OWNER_ID, DRAFT_ID, STATUS, APPROVED_CONTENT_REFERENCE, UPDATED_AT
                    FROM ACTION_STATE
                    WHERE OWNER_ID = ? AND DRAFT_ID = ?
                    """,
                (resultSet, rowNumber) -> new ActionState(
                    resultSet.getString("OWNER_ID"),
                    resultSet.getString("DRAFT_ID"),
                    ActionStatus.valueOf(resultSet.getString("STATUS")),
                    resultSet.getString("APPROVED_CONTENT_REFERENCE"),
                    resultSet.getTimestamp("UPDATED_AT").toInstant()),
                ownerId,
                draftId)
            .stream()
            .findFirst();
    }

    @Override
    public void appendAuditEntry(String ownerId, AuditLogEntry entry) {
        jdbc.update(
            """
                INSERT INTO AUDIT_LOG_ENTRY
                    (ID, OWNER_ID, FOLLOW_UP_ITEM_ID, ACTION_TYPE, OCCURRED_AT,
                     APPROVAL_STATUS, APPROVED_CONTENT_REFERENCE, CREATED,
                     CREATED_BY, CHANGED, CHANGED_BY, VERSION)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?,
                        CURRENT_TIMESTAMP, ?, 0)
                """,
            entry.id(),
            ownerId,
            entry.followUpItemId(),
            entry.actionType(),
            Timestamp.from(entry.timestamp()),
            entry.approvalStatus(),
            entry.approvedContentReference(),
            ownerId,
            ownerId);
    }

    @Override
    public List<AuditLogEntry> findAuditEntries(String ownerId) {
        return jdbc.query(
            """
                SELECT ID, OWNER_ID, FOLLOW_UP_ITEM_ID, ACTION_TYPE, OCCURRED_AT,
                       APPROVAL_STATUS, APPROVED_CONTENT_REFERENCE
                FROM AUDIT_LOG_ENTRY
                WHERE OWNER_ID = ?
                ORDER BY OCCURRED_AT, ID
                """,
            (resultSet, rowNumber) -> auditEntry(resultSet),
            ownerId);
    }

    private ActionDraft draft(ResultSet resultSet) throws SQLException {
        return new ActionDraft(
            resultSet.getString("ID"),
            resultSet.getString("OWNER_ID"),
            ActionType.valueOf(resultSet.getString("ACTION_TYPE")),
            resultSet.getString("FOLLOW_UP_ITEM_ID"),
            deserialize(resultSet.getString("RECIPIENTS")),
            resultSet.getString("SUBJECT"),
            resultSet.getString("BODY"),
            resultSet.getString("MEETING_TITLE"),
            instant(resultSet, "SELECTED_STARTS_AT"),
            instant(resultSet, "SELECTED_ENDS_AT"),
            deserialize(resultSet.getString("AGENDA")),
            resultSet.getString("EDITABLE_CONTEXT"));
    }

    private AuditLogEntry auditEntry(ResultSet resultSet) throws SQLException {
        return new AuditLogEntry(
            resultSet.getString("ID"),
            resultSet.getString("OWNER_ID"),
            resultSet.getString("FOLLOW_UP_ITEM_ID"),
            resultSet.getString("ACTION_TYPE"),
            resultSet.getTimestamp("OCCURRED_AT").toInstant(),
            resultSet.getString("APPROVAL_STATUS"),
            resultSet.getString("APPROVED_CONTENT_REFERENCE"));
    }

    private String serialize(List<String> values) {
        return String.join(LIST_SEPARATOR, values);
    }

    private List<String> deserialize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.asList(value.split(LIST_SEPARATOR, -1));
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        var timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

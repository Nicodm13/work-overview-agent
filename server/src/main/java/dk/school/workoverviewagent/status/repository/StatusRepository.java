package dk.school.workoverviewagent.status.repository;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.model.WorkStatusRecord;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class StatusRepository implements IStatusRepository {

    private final JdbcTemplate jdbc;

    StatusRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String ownerId, String followUpItemId, WorkStatusRecord record) {
        jdbc.update("""
                INSERT INTO WORK_STATUS_RECORD
                    (ID, OWNER_ID, FOLLOW_UP_ITEM_ID, STATUS, REASON, STATUS_SOURCE,
                     UPDATED_AT, CREATED, CREATED_BY, CHANGED, CHANGED_BY, VERSION)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, 0)
                """,
            record.id(),
            ownerId,
            followUpItemId,
            record.status().name(),
            record.reason(),
            record.statusSource().name(),
            Timestamp.from(record.updatedAt()),
            ownerId,
            ownerId);
    }

    @Override
    public List<WorkStatusRecord> findHistory(String ownerId, String followUpItemId) {
        return jdbc.query(
            """
                SELECT ID, OWNER_ID, FOLLOW_UP_ITEM_ID, STATUS, REASON,
                       STATUS_SOURCE, UPDATED_AT
                FROM WORK_STATUS_RECORD
                WHERE OWNER_ID = ? AND FOLLOW_UP_ITEM_ID = ?
                ORDER BY UPDATED_AT, ID
                """,
            (resultSet, rowNumber) -> new WorkStatusRecord(
                resultSet.getString("ID"),
                resultSet.getString("OWNER_ID"),
                resultSet.getString("FOLLOW_UP_ITEM_ID"),
                WorkStatus.valueOf(resultSet.getString("STATUS")),
                resultSet.getString("REASON"),
                StatusSource.valueOf(resultSet.getString("STATUS_SOURCE")),
                resultSet.getTimestamp("UPDATED_AT").toInstant()),
            ownerId,
            followUpItemId);
    }
}

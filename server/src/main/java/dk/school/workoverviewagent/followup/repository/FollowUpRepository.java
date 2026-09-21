package dk.school.workoverviewagent.followup.repository;

import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.FollowUpItem;
import dk.school.workoverviewagent.model.SourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class FollowUpRepository implements IFollowUpRepository {

    private final JdbcTemplate jdbc;

    FollowUpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String ownerId, FollowUpItem item) {
        jdbc.update(
            """
                INSERT INTO FOLLOW_UP_ITEM
                    (ID, OWNER_ID, TITLE, SUMMARY, CREATED, CREATED_BY, CHANGED, CHANGED_BY, VERSION)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, 0)
                """,
            item.id(),
            ownerId,
            item.title(),
            item.summary(),
            ownerId,
            ownerId);
    }

    @Override
    public Optional<FollowUpItem> findById(String ownerId, String followUpItemId) {
        return jdbc.query(
                """
                    SELECT ID, OWNER_ID, TITLE, SUMMARY
                    FROM FOLLOW_UP_ITEM
                    WHERE OWNER_ID = ? AND ID = ?
                    """,
                (resultSet, rowNumber) -> item(resultSet),
                ownerId,
                followUpItemId)
            .stream()
            .findFirst();
    }

    @Override
    public List<FollowUpItem> findAll(String ownerId) {
        return jdbc.query(
            """
                SELECT ID, OWNER_ID, TITLE, SUMMARY
                FROM FOLLOW_UP_ITEM
                WHERE OWNER_ID = ?
                ORDER BY CREATED, ID
                """,
            (resultSet, rowNumber) -> item(resultSet),
            ownerId);
    }

    @Override
    public void linkEvidenceReference(
        String ownerId,
        String followUpItemId,
        String evidenceReferenceId) {
        jdbc.update(
            """
                INSERT INTO FOLLOW_UP_EVIDENCE_REFERENCE
                    (OWNER_ID, FOLLOW_UP_ITEM_ID, EVIDENCE_REFERENCE_ID,
                     CREATED, CREATED_BY, CHANGED, CHANGED_BY, VERSION)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, 0)
                ON CONFLICT DO NOTHING
                """,
            ownerId,
            followUpItemId,
            evidenceReferenceId,
            ownerId,
            ownerId);
    }

    private FollowUpItem item(ResultSet resultSet) throws SQLException {
        var id = resultSet.getString("ID");
        var ownerId = resultSet.getString("OWNER_ID");
        return new FollowUpItem(
            id,
            ownerId,
            resultSet.getString("TITLE"),
            resultSet.getString("SUMMARY"),
            findEvidenceReferences(ownerId, id));
    }

    private List<EvidenceReference> findEvidenceReferences(
        String ownerId,
        String followUpItemId) {
        return jdbc.query(
            """
                SELECT E.ID, E.SOURCE_TYPE, E.SOURCE_ID, E.OCCURRED_AT,
                       E.AUTHOR, E.TITLE, E.EXCERPT, E.CONFIDENCE
                FROM EVIDENCE_REFERENCE E
                JOIN FOLLOW_UP_EVIDENCE_REFERENCE L
                    ON L.EVIDENCE_REFERENCE_ID = E.ID
                   AND L.OWNER_ID = E.OWNER_ID
                WHERE L.OWNER_ID = ?
                  AND L.FOLLOW_UP_ITEM_ID = ?
                ORDER BY E.OCCURRED_AT, E.ID
                """,
            (resultSet, rowNumber) -> reference(resultSet),
            ownerId,
            followUpItemId);
    }

    private EvidenceReference reference(ResultSet resultSet) throws SQLException {
        return new EvidenceReference(
            resultSet.getString("ID"),
            SourceType.valueOf(resultSet.getString("SOURCE_TYPE")),
            resultSet.getString("SOURCE_ID"),
            resultSet.getTimestamp("OCCURRED_AT").toInstant(),
            resultSet.getString("AUTHOR"),
            resultSet.getString("TITLE"),
            resultSet.getString("EXCERPT"),
            resultSet.getDouble("CONFIDENCE"));
    }
}

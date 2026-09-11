package dk.school.workoverviewagent.evidence.repository;

import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.SourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class EvidenceRepository implements IEvidenceRepository {

    private final JdbcTemplate jdbc;

    EvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String ownerId, EvidenceReference reference) {
        jdbc.update(
            """
                INSERT INTO EVIDENCE_REFERENCE
                    (ID, OWNER_ID, SOURCE_TYPE, SOURCE_ID, OCCURRED_AT, AUTHOR,
                     TITLE, EXCERPT, CONFIDENCE, CREATED, CREATED_BY, CHANGED,
                     CHANGED_BY, VERSION)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?,
                        CURRENT_TIMESTAMP, ?, 0)
                ON CONFLICT (ID) DO NOTHING
                """,
            reference.id(),
            ownerId,
            reference.sourceType().name(),
            reference.sourceId(),
            Timestamp.from(reference.timestamp()),
            reference.author(),
            reference.title(),
            reference.excerpt(),
            reference.confidence(),
            ownerId,
            ownerId);
    }

    @Override
    public Optional<EvidenceReference> findById(String ownerId, String evidenceReferenceId) {
        return jdbc.query(
                """
                    SELECT ID, SOURCE_TYPE, SOURCE_ID, OCCURRED_AT, AUTHOR,
                           TITLE, EXCERPT, CONFIDENCE
                    FROM EVIDENCE_REFERENCE
                    WHERE OWNER_ID = ? AND ID = ?
                    """,
                (resultSet, rowNumber) -> reference(resultSet),
                ownerId,
                evidenceReferenceId)
            .stream()
            .findFirst();
    }

    @Override
    public List<EvidenceReference> findByIds(
        String ownerId,
        List<String> evidenceReferenceIds) {
        return evidenceReferenceIds.stream()
            .map(id -> findById(ownerId, id))
            .flatMap(Optional::stream)
            .toList();
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

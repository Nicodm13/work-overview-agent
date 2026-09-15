package dk.school.workoverviewagent.evidence.repository;

import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.SourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class EvidenceRepository implements IEvidenceRepository {

    private final JdbcTemplate jdbc;

    EvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(String ownerId, EvidenceReference reference) {
        var changed = jdbc.update(
            """
                INSERT INTO EVIDENCE_REFERENCE
                    (ID, OWNER_ID, SOURCE_TYPE, SOURCE_ID, OCCURRED_AT, AUTHOR,
                     TITLE, EXCERPT, CONFIDENCE, CREATED, CREATED_BY, CHANGED,
                     CHANGED_BY, VERSION)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?,
                        CURRENT_TIMESTAMP, ?, 0)
                ON CONFLICT (ID) DO UPDATE
                SET SOURCE_TYPE = EXCLUDED.SOURCE_TYPE,
                    SOURCE_ID = EXCLUDED.SOURCE_ID,
                    OCCURRED_AT = EXCLUDED.OCCURRED_AT,
                    AUTHOR = EXCLUDED.AUTHOR,
                    TITLE = EXCLUDED.TITLE,
                    EXCERPT = EXCLUDED.EXCERPT,
                    CONFIDENCE = EXCLUDED.CONFIDENCE,
                    CHANGED = CURRENT_TIMESTAMP,
                    CHANGED_BY = EXCLUDED.CHANGED_BY,
                    VERSION = EVIDENCE_REFERENCE.VERSION + 1
                WHERE EVIDENCE_REFERENCE.SOURCE_TYPE IS DISTINCT FROM EXCLUDED.SOURCE_TYPE
                   OR EVIDENCE_REFERENCE.SOURCE_ID IS DISTINCT FROM EXCLUDED.SOURCE_ID
                   OR EVIDENCE_REFERENCE.OCCURRED_AT IS DISTINCT FROM EXCLUDED.OCCURRED_AT
                   OR EVIDENCE_REFERENCE.AUTHOR IS DISTINCT FROM EXCLUDED.AUTHOR
                   OR EVIDENCE_REFERENCE.TITLE IS DISTINCT FROM EXCLUDED.TITLE
                   OR EVIDENCE_REFERENCE.EXCERPT IS DISTINCT FROM EXCLUDED.EXCERPT
                   OR EVIDENCE_REFERENCE.CONFIDENCE IS DISTINCT FROM EXCLUDED.CONFIDENCE
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

        if (changed > 0) {
            saveVersion(ownerId, reference);
        }
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

    private void saveVersion(String ownerId, EvidenceReference reference) {
        var version = jdbc.queryForObject(
            "SELECT VERSION FROM EVIDENCE_REFERENCE WHERE ID = ?",
            Integer.class,
            reference.id());
        jdbc.update(
            """
                INSERT INTO EVIDENCE_REFERENCE_VERSION
                    (ID, EVIDENCE_REFERENCE_ID, VERSION, OWNER_ID, SOURCE_TYPE,
                     SOURCE_ID, OCCURRED_AT, AUTHOR, TITLE, EXCERPT, CONFIDENCE,
                     RECORDED_AT, CREATED, CREATED_BY, CHANGED, CHANGED_BY)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
                """,
            versionId(reference.id(), version),
            reference.id(),
            version,
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

    private String versionId(String evidenceReferenceId, int version) {
        return UUID.nameUUIDFromBytes((evidenceReferenceId + ':' + version)
            .getBytes(StandardCharsets.UTF_8)).toString();
    }
}

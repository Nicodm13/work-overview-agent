package dk.school.workoverviewagent.evidence.repository;

import dk.school.workoverviewagent.model.EvidenceReference;

import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for owner-scoped, minimized source evidence.
 */
public interface IEvidenceRepository {
    void save(String ownerId, EvidenceReference reference);

    Optional<EvidenceReference> findById(String ownerId, String evidenceReferenceId);

    List<EvidenceReference> findByIds(String ownerId, List<String> evidenceReferenceIds);
}

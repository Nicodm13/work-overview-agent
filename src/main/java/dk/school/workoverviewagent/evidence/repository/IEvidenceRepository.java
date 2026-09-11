package dk.school.workoverviewagent.evidence.repository;

import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.EvidenceReference;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for owner-scoped captured evidence and source references. */
public interface IEvidenceRepository {
    void save(String ownerId, EvidenceItem evidenceItem);
    Optional<EvidenceItem> findById(String ownerId, String evidenceId);
    List<EvidenceReference> findReferences(String ownerId, String evidenceId);
}

package dk.school.workoverviewagent.followup.repository;

import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.FollowUpItem;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for owner-scoped follow-up items and their explicit evidence links. */
public interface IFollowUpRepository {
    void save(String ownerId, FollowUpItem item);
    Optional<FollowUpItem> findById(String ownerId, String followUpItemId);
    List<FollowUpItem> findAll(String ownerId);
    void addEvidenceReference(String ownerId, String followUpItemId, EvidenceReference reference);
    List<EvidenceReference> findEvidenceReferences(String ownerId, String followUpItemId);
}

package dk.school.workoverviewagent.action.repository;

import dk.school.workoverviewagent.model.ActionDraft;
import dk.school.workoverviewagent.model.ActionState;
import dk.school.workoverviewagent.model.AuditLogEntry;

import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for owner-scoped drafts, action lifecycle state, and audit entries.
 */
public interface IActionRepository {
    void saveDraft(String ownerId, ActionDraft draft);

    Optional<ActionDraft> findDraftById(String ownerId, String draftId);

    void saveActionState(ActionState actionState);

    Optional<ActionState> findActionState(String ownerId, String draftId);

    void appendAuditEntry(String ownerId, AuditLogEntry entry);

    List<AuditLogEntry> findAuditEntries(String ownerId);
}

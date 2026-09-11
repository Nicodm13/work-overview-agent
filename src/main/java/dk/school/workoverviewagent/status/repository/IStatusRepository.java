package dk.school.workoverviewagent.status.repository;

import dk.school.workoverviewagent.model.WorkStatusRecord;
import java.util.List;

/** Persistence boundary for the owner-scoped, append-only work-status history. */
public interface IStatusRepository {
    void append(String ownerId, String followUpItemId, WorkStatusRecord record);
    List<WorkStatusRecord> findHistory(String ownerId, String followUpItemId);
}

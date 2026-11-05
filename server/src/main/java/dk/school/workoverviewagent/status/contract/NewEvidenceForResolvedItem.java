package dk.school.workoverviewagent.status.contract;

import dk.school.workoverviewagent.model.EvidenceReference;

import java.time.Instant;

/**
 * Linked evidence whose source timestamp is after the item's latest user-confirmed resolution.
 * It does not determine whether the item should be reopened.
 */
public record NewEvidenceForResolvedItem(
    String followUpItemId,
    String followUpItemTitle,
    Instant resolvedAt,
    EvidenceReference evidenceReference) {
}

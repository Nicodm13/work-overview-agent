package dk.school.workoverviewagent.status.contract;

import dk.school.workoverviewagent.model.EvidenceReference;

import java.time.Instant;

/**
 * Factual evidence linked to an item after the user last marked that item as resolved.
 * It does not determine whether the item should be reopened.
 */
public record NewEvidenceForResolvedItem(
    String followUpItemId,
    String followUpItemTitle,
    Instant resolvedAt,
    EvidenceReference evidenceReference) {
}

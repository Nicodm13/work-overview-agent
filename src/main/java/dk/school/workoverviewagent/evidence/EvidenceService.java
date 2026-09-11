package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.evidence.contract.EvidenceResponse;
import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class EvidenceService implements IEvidenceService {

    private final Map<String, EvidenceItem> latestEvidenceByOwnerAndId = new LinkedHashMap<>();

    @Override
    public synchronized List<EvidenceItem> captureEvidence(ReviewRequest request, SourceData sourceData) {
        validateOwnerId(request.ownerId());
        if (sourceData == null) {
            return List.of();
        }

        var evidenceItems = sourceData.items().stream()
            .map(this::toEvidenceItem)
            .toList();
        latestEvidenceByOwnerAndId.entrySet().removeIf(entry -> entry.getKey().startsWith(request.ownerId() + ':'));
        evidenceItems.forEach(evidence -> latestEvidenceByOwnerAndId.put(key(request.ownerId(), evidence.id()), evidence));
        return evidenceItems;
    }

    @Override
    public synchronized EvidenceResponse getEvidence(String ownerId, String evidenceId) {
        validateOwnerId(ownerId);
        var evidence = latestEvidenceByOwnerAndId.get(key(ownerId, evidenceId));
        return new EvidenceResponse(
            ownerId,
            evidenceId,
            evidence == null ? List.of() : evidence.references());
    }

    private String key(String ownerId, String evidenceId) {
        return ownerId + ':' + evidenceId;
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    private EvidenceItem toEvidenceItem(SourceItem sourceItem) {
        return new EvidenceItem(
            "evidence-" + sourceItem.id(),
            sourceItem.title(),
            evidenceSummary(sourceItem),
            EvidenceStatus.SOURCE_EVIDENCE_CAPTURED,
            List.of(reference(sourceItem)));
    }

    private EvidenceReference reference(SourceItem sourceItem) {
        return new EvidenceReference(
            sourceItem.sourceType(),
            sourceItem.id(),
            sourceItem.occurredAt(),
            sourceItem.senderOrOrganizer(),
            sourceItem.title(),
            excerpt(sourceItem.content()),
            1.0);
    }

    private String evidenceSummary(SourceItem sourceItem) {
        return "Source evidence captured from " + sourceItem.sourceType() + ".";
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

}

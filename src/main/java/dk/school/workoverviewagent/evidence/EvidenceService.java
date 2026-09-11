package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.evidence.contract.EvidenceResponse;
import dk.school.workoverviewagent.evidence.repository.IEvidenceRepository;
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

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
class EvidenceService implements IEvidenceService {

    private final IEvidenceRepository evidenceRepository;

    EvidenceService(IEvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    public List<EvidenceItem> captureEvidence(ReviewRequest request, SourceData sourceData) {
        validateOwnerId(request.ownerId());
        if (sourceData == null) {
            return List.of();
        }

        var evidenceItems = sourceData.items().stream()
            .map(sourceItem -> toEvidenceItem(request.ownerId(), sourceItem))
            .toList();
        evidenceItems.forEach(evidence -> evidence.references().forEach(
            reference -> evidenceRepository.save(request.ownerId(), reference)));
        return evidenceItems;
    }

    @Override
    public EvidenceResponse getEvidence(String ownerId, String evidenceReferenceId) {
        validateOwnerId(ownerId);
        var reference = evidenceRepository.findById(ownerId, evidenceReferenceId);
        return new EvidenceResponse(
            ownerId,
            evidenceReferenceId,
            reference.map(List::of).orElseGet(List::of));
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    private EvidenceItem toEvidenceItem(String ownerId, SourceItem sourceItem) {
        return new EvidenceItem(
            "evidence-" + sourceItem.id(),
            sourceItem.title(),
            evidenceSummary(sourceItem),
            EvidenceStatus.SOURCE_EVIDENCE_CAPTURED,
            List.of(reference(ownerId, sourceItem)));
    }

    private EvidenceReference reference(String ownerId, SourceItem sourceItem) {
        return new EvidenceReference(
            UUID.nameUUIDFromBytes((ownerId + ':' + sourceItem.sourceType() + ':' + sourceItem.id())
                .getBytes(StandardCharsets.UTF_8))
                .toString(),
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

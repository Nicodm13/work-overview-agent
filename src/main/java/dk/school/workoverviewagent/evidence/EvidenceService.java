package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.evidence.contract.EvidenceResponse;
import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class EvidenceService implements IEvidenceService {

    private final Map<String, EvidenceItem> latestFindingsById = new LinkedHashMap<>();

    @Override
    public synchronized List<EvidenceItem> captureEvidence(ReviewRequest request, SourceData sourceData) {
        if (sourceData == null) {
            return List.of();
        }

        var findings = sourceData.items().stream()
                .map(this::toEvidenceItem)
                .toList();
        latestFindingsById.clear();
        findings.forEach(finding -> latestFindingsById.put(finding.id(), finding));
        return findings;
    }

    @Override
    public synchronized EvidenceResponse getEvidence(String evidenceId) {
        var finding = latestFindingsById.get(evidenceId);
        return new EvidenceResponse(
                evidenceId,
                finding == null ? List.of() : finding.references());
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

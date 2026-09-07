package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.model.EvidenceStatus;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class EvidenceService implements IEvidenceService {

    @Override
    public List<EvidenceItem> identifyFollowUps(ReviewRequest request, SourceData sourceData) {
        if (sourceData == null) {
            return List.of();
        }
        return sourceData.items().stream()
                .map(this::toEvidenceItem)
                .toList();
    }

    private EvidenceItem toEvidenceItem(SourceItem sourceItem) {
        return new EvidenceItem(
                "evidence-" + sourceItem.id(),
                sourceItem.title(),
                "Digital evidence from " + sourceItem.sourceType() + ": " + excerpt(sourceItem.content()),
                priority(sourceItem),
                EvidenceStatus.POSSIBLE_FOLLOW_UP_FOUND);
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    private String priority(SourceItem sourceItem) {
        var text = ((sourceItem.title() == null ? "" : sourceItem.title())
                + " "
                + (sourceItem.content() == null ? "" : sourceItem.content())).toLowerCase();
        if (text.contains("decision needed") || text.contains("before") || text.contains("?")) {
            return "HIGH";
        }
        if (text.contains("prepare") || text.contains("agreed")) {
            return "MEDIUM";
        }
        return "LOW";
    }
}

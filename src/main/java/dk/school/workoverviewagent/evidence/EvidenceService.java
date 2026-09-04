package dk.school.workoverviewagent.evidence;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.review.contract.ReviewScope;
import dk.school.workoverviewagent.source.SourceData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class EvidenceService implements IEvidenceService {

    @Override
    public List<EvidenceItem> identifyFollowUps(String userId, ReviewScope scope, SourceData sourceData) {
        // TODO #9: Extract possible follow-up items and evidence references from normalized source data.
        return null;
    }
}

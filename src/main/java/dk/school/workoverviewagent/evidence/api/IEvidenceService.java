package dk.school.workoverviewagent.evidence.api;

import dk.school.workoverviewagent.evidence.EvidenceItem;
import dk.school.workoverviewagent.review.contract.ReviewScope;
import dk.school.workoverviewagent.source.SourceData;
import java.util.List;

public interface IEvidenceService {

    List<EvidenceItem> identifyFollowUps(String userId, ReviewScope scope, SourceData sourceData);
}

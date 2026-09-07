package dk.school.workoverviewagent.evidence.api;

import dk.school.workoverviewagent.evidence.EvidenceItem;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import java.util.List;

public interface IEvidenceService {

    List<EvidenceItem> identifyFollowUps(ReviewRequest request, SourceData sourceData);
}

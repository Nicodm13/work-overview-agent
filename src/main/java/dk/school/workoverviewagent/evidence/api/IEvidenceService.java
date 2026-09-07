package dk.school.workoverviewagent.evidence.api;

import dk.school.workoverviewagent.evidence.contract.EvidenceResponse;
import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.source.contract.SourceData;
import java.util.List;

public interface IEvidenceService {

    List<EvidenceItem> captureEvidence(ReviewRequest request, SourceData sourceData);

    EvidenceResponse getEvidence(String evidenceId);
}

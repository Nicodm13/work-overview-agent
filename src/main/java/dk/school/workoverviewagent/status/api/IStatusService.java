package dk.school.workoverviewagent.status.api;

import dk.school.workoverviewagent.evidence.EvidenceItem;
import dk.school.workoverviewagent.status.StatusItem;
import java.util.List;

public interface IStatusService {

    List<StatusItem> applyCurrentStatus(String userId, List<EvidenceItem> evidenceItems);
}

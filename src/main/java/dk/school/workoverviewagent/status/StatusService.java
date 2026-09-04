package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.evidence.EvidenceItem;
import dk.school.workoverviewagent.status.api.IStatusService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class StatusService implements IStatusService {

    @Override
    public List<StatusItem> applyCurrentStatus(String userId, List<EvidenceItem> evidenceItems) {
        // TODO #12: Apply user-confirmed work status and preserve evidence/work status distinction.
        return null;
    }
}

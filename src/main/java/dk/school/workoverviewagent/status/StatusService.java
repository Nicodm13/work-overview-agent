package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.evidence.EvidenceItem;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.status.api.IStatusService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class StatusService implements IStatusService {

    @Override
    public List<StatusItem> applyCurrentStatus(List<EvidenceItem> evidenceItems) {
        if (evidenceItems == null) {
            return List.of();
        }
        return evidenceItems.stream()
                .map(evidenceItem -> new StatusItem(evidenceItem, WorkStatus.UNVERIFIED, StatusSource.DIGITAL_EVIDENCE))
                .toList();
    }
}

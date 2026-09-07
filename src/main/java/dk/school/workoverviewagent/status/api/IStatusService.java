package dk.school.workoverviewagent.status.api;

import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.StatusItem;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.GetWorkStatusResponse;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusResponse;
import java.util.List;

public interface IStatusService {

    List<StatusItem> applyCurrentStatus(List<EvidenceItem> evidenceItems);

    GetWorkStatusResponse getWorkStatus(GetWorkStatusRequest request);

    UpdateWorkStatusResponse updateWorkStatus(UpdateWorkStatusRequest request);
}

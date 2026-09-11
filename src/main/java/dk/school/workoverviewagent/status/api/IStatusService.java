package dk.school.workoverviewagent.status.api;

import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.GetWorkStatusResponse;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusResponse;

public interface IStatusService {

    GetWorkStatusResponse getWorkStatus(GetWorkStatusRequest request);

    UpdateWorkStatusResponse updateWorkStatus(UpdateWorkStatusRequest request);
}

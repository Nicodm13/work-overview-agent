package dk.school.workoverviewagent.status.contract;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import java.time.Instant;

public record UpdateWorkStatusRequest(
        String ownerId,
        String followUpItemId,
        WorkStatus workStatus,
        String reason,
        StatusSource statusSource,
        Instant updatedAt) {
}

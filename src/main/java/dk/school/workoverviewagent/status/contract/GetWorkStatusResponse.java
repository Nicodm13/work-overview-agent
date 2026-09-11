package dk.school.workoverviewagent.status.contract;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.model.WorkStatusRecord;
import java.time.Instant;
import java.util.List;

public record GetWorkStatusResponse(
        String followUpItemId,
        WorkStatus workStatus,
        StatusSource statusSource,
        String reason,
        Instant updatedAt,
        List<WorkStatusRecord> history) {

    public GetWorkStatusResponse {
        history = history == null ? List.of() : List.copyOf(history);
    }
}

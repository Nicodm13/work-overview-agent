package dk.school.workoverviewagent.status.contract;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.model.WorkStatusRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetWorkStatusResponse(
    String ownerId,
    String followUpItemId,
    WorkStatus workStatus,
    StatusSource statusSource,
    String reason,
    @Nullable @JsonProperty(required = false) Instant updatedAt,
    List<WorkStatusRecord> history) {

    public GetWorkStatusResponse {
        history = history == null ? List.of() : List.copyOf(history);
    }
}

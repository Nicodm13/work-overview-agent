package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.model.WorkStatusRecord;
import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.status.api.IStatusService;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.GetWorkStatusResponse;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class StatusService implements IStatusService {

    private final IFollowUpService followUpService;
    private final Map<String, List<WorkStatusRecord>> historyByOwnerAndFollowUpItemId = new LinkedHashMap<>();

    StatusService(IFollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    @Override
    public synchronized GetWorkStatusResponse getWorkStatus(GetWorkStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateFollowUpItemId(request.followUpItemId());
        followUpService.getFollowUpItem(request.ownerId(), request.followUpItemId());
        validateOwnerId(request.ownerId());

        var history = historyFor(request.ownerId(), request.followUpItemId());
        if (history.isEmpty()) {
            return new GetWorkStatusResponse(
                    request.ownerId(),
                    request.followUpItemId(),
                    WorkStatus.UNVERIFIED,
                    StatusSource.DIGITAL_EVIDENCE,
                    "No user-confirmed status recorded.",
                    null,
                    List.of());
        }

        var current = history.getLast();
        return new GetWorkStatusResponse(
                request.ownerId(),
                request.followUpItemId(),
                current.status(),
                current.statusSource(),
                current.reason(),
                current.updatedAt(),
                history);
    }

    @Override
    public synchronized UpdateWorkStatusResponse updateWorkStatus(UpdateWorkStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateFollowUpItemId(request.followUpItemId());
        followUpService.getFollowUpItem(request.ownerId(), request.followUpItemId());
        validateOwnerId(request.ownerId());
        Objects.requireNonNull(request.workStatus(), "workStatus must not be null");
        validateStatusSource(request.statusSource());

        var updatedAt = request.updatedAt() == null ? Instant.now() : request.updatedAt();
        var record = new WorkStatusRecord(
                UUID.randomUUID().toString(),
                request.ownerId(),
                request.followUpItemId(),
                request.workStatus(),
                request.reason() == null ? "" : request.reason(),
                request.statusSource(),
                updatedAt);

        historyByOwnerAndFollowUpItemId.computeIfAbsent(key(request.ownerId(), request.followUpItemId()), ignored -> new ArrayList<>()).add(record);
        var history = historyFor(request.ownerId(), request.followUpItemId());
        return new UpdateWorkStatusResponse(
                request.ownerId(),
                request.followUpItemId(),
                record.status(),
                record.statusSource(),
                record.reason(),
                record.updatedAt(),
                history);
    }

    private List<WorkStatusRecord> historyFor(String ownerId, String followUpItemId) {
        return List.copyOf(historyByOwnerAndFollowUpItemId.getOrDefault(key(ownerId, followUpItemId), List.of()));
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    private String key(String ownerId, String followUpItemId) {
        return ownerId + ':' + followUpItemId;
    }

    private void validateFollowUpItemId(String followUpItemId) {
        if (followUpItemId == null || followUpItemId.isBlank()) {
            throw new IllegalArgumentException("followUpItemId must not be blank");
        }
    }

    private void validateStatusSource(StatusSource statusSource) {
        Objects.requireNonNull(statusSource, "statusSource must not be null");
        if (statusSource != StatusSource.USER_CONFIRMED) {
            throw new IllegalArgumentException("work status updates must be user-confirmed");
        }
    }
}

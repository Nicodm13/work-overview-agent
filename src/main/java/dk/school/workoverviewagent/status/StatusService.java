package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.model.WorkStatusRecord;
import dk.school.workoverviewagent.status.api.IStatusService;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.GetWorkStatusResponse;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusResponse;
import dk.school.workoverviewagent.status.repository.IStatusRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
class StatusService implements IStatusService {

    private final IFollowUpService followUpService;
    private final IStatusRepository statusRepository;

    StatusService(
        IFollowUpService followUpService,
        IStatusRepository statusRepository) {
        this.followUpService = followUpService;
        this.statusRepository = statusRepository;
    }

    @Override
    public GetWorkStatusResponse getWorkStatus(GetWorkStatusRequest request) {
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
    public UpdateWorkStatusResponse updateWorkStatus(UpdateWorkStatusRequest request) {
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

        statusRepository.append(request.ownerId(), request.followUpItemId(), record);
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
        return statusRepository.findHistory(ownerId, followUpItemId);
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
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

package dk.school.workoverviewagent.status;

import dk.school.workoverviewagent.model.EvidenceItem;
import dk.school.workoverviewagent.model.StatusItem;
import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.model.WorkStatusRecord;
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

    private final Map<String, List<WorkStatusRecord>> historyByEvidenceId = new LinkedHashMap<>();

    @Override
    public synchronized List<StatusItem> applyCurrentStatus(List<EvidenceItem> evidenceItems) {
        if (evidenceItems == null) {
            return List.of();
        }
        return evidenceItems.stream()
                .map(this::applyCurrentStatus)
                .toList();
    }

    @Override
    public synchronized GetWorkStatusResponse getWorkStatus(GetWorkStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateEvidenceId(request.evidenceId());

        var history = historyFor(request.evidenceId());
        if (history.isEmpty()) {
            return new GetWorkStatusResponse(
                    request.evidenceId(),
                    WorkStatus.UNVERIFIED,
                    StatusSource.DIGITAL_EVIDENCE,
                    "No user-confirmed status recorded.",
                    null,
                    List.of());
        }

        var current = history.getLast();
        return new GetWorkStatusResponse(
                request.evidenceId(),
                current.status(),
                current.statusSource(),
                current.reason(),
                current.updatedAt(),
                history);
    }

    @Override
    public synchronized UpdateWorkStatusResponse updateWorkStatus(UpdateWorkStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateEvidenceId(request.evidenceId());
        Objects.requireNonNull(request.workStatus(), "workStatus must not be null");
        validateStatusSource(request.statusSource());

        var updatedAt = request.updatedAt() == null ? Instant.now() : request.updatedAt();
        var record = new WorkStatusRecord(
                UUID.randomUUID().toString(),
                request.evidenceId(),
                request.workStatus(),
                request.reason() == null ? "" : request.reason(),
                request.statusSource(),
                updatedAt);

        historyByEvidenceId.computeIfAbsent(request.evidenceId(), ignored -> new ArrayList<>()).add(record);
        var history = historyFor(request.evidenceId());
        return new UpdateWorkStatusResponse(
                request.evidenceId(),
                record.status(),
                record.statusSource(),
                record.reason(),
                record.updatedAt(),
                history);
    }

    private StatusItem applyCurrentStatus(EvidenceItem evidenceItem) {
        var history = historyFor(evidenceItem.id());
        if (history.isEmpty()) {
            return new StatusItem(evidenceItem, WorkStatus.UNVERIFIED, StatusSource.DIGITAL_EVIDENCE, List.of());
        }

        var current = history.getLast();
        return new StatusItem(evidenceItem, current.status(), current.statusSource(), history);
    }

    private List<WorkStatusRecord> historyFor(String evidenceId) {
        return List.copyOf(historyByEvidenceId.getOrDefault(evidenceId, List.of()));
    }

    private void validateEvidenceId(String evidenceId) {
        if (evidenceId == null || evidenceId.isBlank()) {
            throw new IllegalArgumentException("evidenceId must not be blank");
        }
    }

    private void validateStatusSource(StatusSource statusSource) {
        Objects.requireNonNull(statusSource, "statusSource must not be null");
        if (statusSource != StatusSource.USER_CONFIRMED) {
            throw new IllegalArgumentException("work status updates must be user-confirmed");
        }
    }
}

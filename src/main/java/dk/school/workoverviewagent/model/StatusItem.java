package dk.school.workoverviewagent.model;

import java.util.List;

public record StatusItem(
        EvidenceItem evidenceItem,
        WorkStatus workStatus,
        StatusSource statusSource,
        List<WorkStatusRecord> history) {

    public StatusItem {
        history = history == null ? List.of() : List.copyOf(history);
    }
}

package dk.school.workoverviewagent.source.mock;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class MockCalendarSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;

    MockCalendarSourceAdapter(SourceItemFilter sourceItemFilter) {
        this.sourceItemFilter = sourceItemFilter;
    }

    private static final List<SourceItem> ITEMS = List.of(
            new SourceItem(
                    "calendar-2026-09-04-001",
                    SourceType.CALENDAR,
                    Instant.parse("2026-09-04T12:00:00Z"),
                    "Project Alpha status review",
                    "Review open decisions, integration risks, and test environment readiness.",
                    "Maja Jensen",
                    List.of("user-1", "Maja Jensen", "Jonas Holm", "Sara Lind"),
                    Map.of("startsAt", "2026-09-04T12:00:00Z", "endsAt", "2026-09-04T12:30:00Z")),
            new SourceItem(
                    "calendar-2026-09-07-001",
                    SourceType.CALENDAR,
                    Instant.parse("2026-09-07T08:30:00Z"),
                    "Prototype demo preparation",
                    "Prepare the work overview demo flow and evidence examples.",
                    "user-1",
                    List.of("user-1", "Supervisor"),
                    Map.of("startsAt", "2026-09-07T08:30:00Z", "endsAt", "2026-09-07T09:00:00Z")));

    @Override
    public SourceType sourceType() {
        return SourceType.CALENDAR;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.CALENDAR, sourceItemFilter.matching(request, ITEMS), List.of());
    }
}

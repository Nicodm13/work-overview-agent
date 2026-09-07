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
class MockTeamsSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;

    MockTeamsSourceAdapter(SourceItemFilter sourceItemFilter) {
        this.sourceItemFilter = sourceItemFilter;
    }

    private static final List<SourceItem> ITEMS = List.of(
            new SourceItem(
                    "teams-2026-09-04-001",
                    SourceType.TEAMS,
                    Instant.parse("2026-09-04T08:20:00Z"),
                    "Test environment clarification",
                    "Can you confirm whether the test environment is ready for use?",
                    "Maja Jensen",
                    List.of("user-1", "Maja Jensen"),
                    Map.of("team", "Delivery", "channel", "Project Alpha")),
            new SourceItem(
                    "teams-2026-09-04-002",
                    SourceType.TEAMS,
                    Instant.parse("2026-09-04T13:45:00Z"),
                    "API contract follow-up",
                    "We still need your answer on the API contract before tomorrow's review.",
                    "Jonas Holm",
                    List.of("user-1", "Jonas Holm"),
                    Map.of("team", "Delivery", "channel", "Integrations")));

    @Override
    public SourceType sourceType() {
        return SourceType.TEAMS;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.TEAMS, sourceItemFilter.matching(request, ITEMS), List.of());
    }
}

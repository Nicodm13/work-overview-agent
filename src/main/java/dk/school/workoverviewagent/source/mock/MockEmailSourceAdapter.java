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
class MockEmailSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;

    MockEmailSourceAdapter(SourceItemFilter sourceItemFilter) {
        this.sourceItemFilter = sourceItemFilter;
    }

    private static final List<SourceItem> ITEMS = List.of(
            new SourceItem(
                    "email-2026-09-04-001",
                    SourceType.OUTLOOK,
                    Instant.parse("2026-09-04T09:05:00Z"),
                    "Decision needed: integration approach",
                    "Please send your recommendation for the integration approach before Friday noon.",
                    "Sara Lind",
                    List.of("user-1", "Sara Lind"),
                    Map.of("folder", "Inbox", "importance", "high")),
            new SourceItem(
                    "email-2026-09-05-001",
                    SourceType.OUTLOOK,
                    Instant.parse("2026-09-05T10:10:00Z"),
                    "Updated onboarding notes",
                    "The onboarding notes have been updated for next week's walkthrough.",
                    "Emil Bro",
                    List.of("user-1", "Emil Bro"),
                    Map.of("folder", "Inbox", "importance", "normal")));

    @Override
    public SourceType sourceType() {
        return SourceType.OUTLOOK;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.OUTLOOK, sourceItemFilter.matching(request, ITEMS), List.of());
    }
}

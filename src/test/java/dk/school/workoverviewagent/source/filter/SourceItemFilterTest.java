package dk.school.workoverviewagent.source.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceItemFilterTest {

    private final SourceItemFilter sourceItemFilter = new SourceItemFilter();

    @Test
    void shouldApplyIntervalAndTextFiltersToNormalizedSourceItems() {
        var matchingItem = new SourceItem(
                "source-1",
                SourceType.TEAMS,
                Instant.parse("2026-09-04T08:20:00Z"),
                "Test environment clarification",
                "Can you confirm readiness?",
                "Maja Jensen",
                List.of("user-1", "Maja Jensen"),
                Map.of("channel", "Project Alpha"));
        var outsideInterval = new SourceItem(
                "source-2",
                SourceType.TEAMS,
                Instant.parse("2026-09-05T08:20:00Z"),
                "Test environment clarification",
                "Can you confirm readiness?",
                "Maja Jensen",
                List.of("user-1", "Maja Jensen"),
                Map.of("channel", "Project Alpha"));
        var outsideTextFilter = new SourceItem(
                "source-3",
                SourceType.TEAMS,
                Instant.parse("2026-09-04T09:20:00Z"),
                "Release notes",
                "The draft is ready.",
                "Jonas Holm",
                List.of("user-1", "Jonas Holm"),
                Map.of("channel", "Documentation"));

        var result = sourceItemFilter.matching(
                new SourceRequest(
                        "user-1",
                        Instant.parse("2026-09-04T00:00:00Z"),
                        Instant.parse("2026-09-04T23:59:59Z"),
                        List.of(SourceType.TEAMS),
                        "test environment"),
                List.of(matchingItem, outsideInterval, outsideTextFilter));

        assertThat(result).containsExactly(matchingItem);
    }
}

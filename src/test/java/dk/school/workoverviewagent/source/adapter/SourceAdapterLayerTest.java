package dk.school.workoverviewagent.source.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SourceAdapterLayerTest {

    @Autowired
    private ISourceAdapterLayer sourceAdapterLayer;

    @Test
    void shouldLoadDeterministicMockDataForAllPrototypeSources() {
        var result = sourceAdapterLayer.loadSources(new SourceRequest(
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T23:59:59Z"),
                List.of(),
                null));

        assertThat(result.limitations()).isEmpty();
        assertThat(result.items())
                .extracting(SourceItem::id)
                .containsExactly(
                        "teams-2026-09-04-001",
                        "email-2026-09-04-001",
                        "calendar-2026-09-04-001",
                        "meeting-notes-2026-09-04-001",
                        "teams-2026-09-04-002");
        assertThat(result.items())
                .extracting(SourceItem::sourceType)
                .contains(SourceType.TEAMS, SourceType.OUTLOOK, SourceType.CALENDAR, SourceType.MEETING_NOTES);
    }

    @Test
    void shouldRespectSelectedSourceFilters() {
        var result = sourceAdapterLayer.loadSources(new SourceRequest(
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T23:59:59Z"),
                List.of(SourceType.TEAMS, SourceType.CALENDAR),
                null));

        assertThat(result.items())
                .extracting(SourceItem::sourceType)
                .containsOnly(SourceType.TEAMS, SourceType.CALENDAR);
        assertThat(result.items()).hasSize(3);
    }

    @Test
    void shouldRespectIntervalAndTextFilters() {
        var result = sourceAdapterLayer.loadSources(new SourceRequest(
                Instant.parse("2026-09-03T00:00:00Z"),
                Instant.parse("2026-09-04T23:59:59Z"),
                List.of(),
                "test environment"));

        assertThat(result.items())
                .extracting(SourceItem::id)
                .containsExactly(
                        "meeting-notes-2026-09-03-001",
                        "teams-2026-09-04-001",
                        "calendar-2026-09-04-001");
    }
}

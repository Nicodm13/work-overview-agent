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
class MockMeetingNotesSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;

    MockMeetingNotesSourceAdapter(SourceItemFilter sourceItemFilter) {
        this.sourceItemFilter = sourceItemFilter;
    }

    private static final List<SourceItem> ITEMS = List.of(
            new SourceItem(
                    "meeting-notes-2026-09-03-001",
                    SourceType.MEETING_NOTES,
                    Instant.parse("2026-09-03T14:00:00Z"),
                    "Project Alpha planning notes",
                    "Maja will investigate whether the test environment can be used. A status update is expected by Friday.",
                    "Maja Jensen",
                    List.of("user-1", "Maja Jensen", "Jonas Holm"),
                    Map.of("meetingTitle", "Project Alpha planning")),
            new SourceItem(
                    "meeting-notes-2026-09-04-001",
                    SourceType.MEETING_NOTES,
                    Instant.parse("2026-09-04T12:30:00Z"),
                    "Project Alpha status review notes",
                    "The team agreed to clarify API ownership and collect questions before the next review.",
                    "Sara Lind",
                    List.of("user-1", "Maja Jensen", "Jonas Holm", "Sara Lind"),
                    Map.of("meetingTitle", "Project Alpha status review")));

    @Override
    public SourceType sourceType() {
        return SourceType.MEETING_NOTES;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.MEETING_NOTES, sourceItemFilter.matching(request, ITEMS), List.of());
    }
}

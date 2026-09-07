package dk.school.workoverviewagent.source.mock.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import java.util.List;

import dk.school.workoverviewagent.source.mock.MockSourceDataLoader;
import org.springframework.stereotype.Component;

@Component
class MockMeetingNotesSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;
    private final List<SourceItem> items;

    MockMeetingNotesSourceAdapter(SourceItemFilter sourceItemFilter, MockSourceDataLoader dataLoader) {
        this.sourceItemFilter = sourceItemFilter;
        this.items = dataLoader.loadItems("meeting-notes.csv", SourceType.MEETING_NOTES);
    }

    @Override
    public SourceType sourceType() {
        return SourceType.MEETING_NOTES;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.MEETING_NOTES, sourceItemFilter.matching(request, items), List.of());
    }
}

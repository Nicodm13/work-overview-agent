package dk.school.workoverviewagent.source.mock.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import dk.school.workoverviewagent.source.mock.MockSourceDataLoader;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "mock")
class MockMeetingNotesSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;
    private final MockSourceDataLoader dataLoader;
    private final IUserProvider userProvider;

    MockMeetingNotesSourceAdapter(
        SourceItemFilter sourceItemFilter,
        MockSourceDataLoader dataLoader,
        IUserProvider userProvider) {
        this.sourceItemFilter = sourceItemFilter;
        this.dataLoader = dataLoader;
        this.userProvider = userProvider;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.MEETING_NOTES;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        var items = dataLoader.loadMeetingNoteItemsForUser(userProvider.getUserPrincipalName());
        return new SourceResult(SourceType.MEETING_NOTES, sourceItemFilter.matching(request, items), List.of());
    }
}

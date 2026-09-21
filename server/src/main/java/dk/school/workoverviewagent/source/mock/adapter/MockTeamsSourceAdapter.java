package dk.school.workoverviewagent.source.mock.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.filter.SourceItemFilter;
import dk.school.workoverviewagent.source.mock.MockSourceDataLoader;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MockTeamsSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;
    private final List<SourceItem> items;

    MockTeamsSourceAdapter(SourceItemFilter sourceItemFilter, MockSourceDataLoader dataLoader) {
        this.sourceItemFilter = sourceItemFilter;
        this.items = dataLoader.loadItems("teams.csv", SourceType.TEAMS);
    }

    @Override
    public SourceType sourceType() {
        return SourceType.TEAMS;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.TEAMS, sourceItemFilter.matching(request, items), List.of());
    }
}

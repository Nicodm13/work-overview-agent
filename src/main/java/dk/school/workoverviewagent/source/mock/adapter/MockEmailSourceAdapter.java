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
class MockEmailSourceAdapter implements ISourceAdapter {

    private final SourceItemFilter sourceItemFilter;
    private final List<SourceItem> items;

    MockEmailSourceAdapter(SourceItemFilter sourceItemFilter, MockSourceDataLoader dataLoader) {
        this.sourceItemFilter = sourceItemFilter;
        this.items = dataLoader.loadItems("email.csv", SourceType.OUTLOOK);
    }

    @Override
    public SourceType sourceType() {
        return SourceType.OUTLOOK;
    }

    @Override
    public SourceResult load(SourceRequest request) {
        return new SourceResult(SourceType.OUTLOOK, sourceItemFilter.matching(request, items), List.of());
    }
}

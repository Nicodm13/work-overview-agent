package dk.school.workoverviewagent.source.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Component
class SourceAdapterLayer implements ISourceAdapterLayer {

    private final List<ISourceAdapter> adapters;

    SourceAdapterLayer(List<ISourceAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    private static EnumSet<SourceType> selectedSourceTypes(SourceRequest request) {
        if (request.sourceTypes().isEmpty()) {
            return EnumSet.allOf(SourceType.class);
        }
        return EnumSet.copyOf(request.sourceTypes());
    }

    @Override
    public SourceData loadSources(SourceRequest request) {
        Objects.requireNonNull(request, "source request must not be null");

        var selectedSourceTypes = selectedSourceTypes(request);
        var results = adapters.stream()
            .filter(adapter -> selectedSourceTypes.contains(adapter.sourceType()))
            .map(adapter -> adapter.load(request))
            .toList();

        var items = results.stream()
            .flatMap(result -> result.items().stream())
            .sorted(Comparator.comparing(SourceItem::occurredAt).thenComparing(SourceItem::id))
            .toList();
        var limitations = results.stream()
            .flatMap(result -> result.limitations().stream())
            .toList();

        return new SourceData(items, limitations);
    }
}

package dk.school.workoverviewagent.source.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceCoverage;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        var loadedItems = new ArrayList<SourceItem>();
        var limitations = new ArrayList<String>();
        var successfulSources = 0;

        for (var sourceType : selectedSourceTypes) {
            var adapter = adapters.stream()
                .filter(candidate -> candidate.sourceType() == sourceType)
                .findFirst();
            if (adapter.isEmpty()) {
                limitations.add(sourceType + " source is not configured; its results are unavailable.");
                continue;
            }
            try {
                var result = Objects.requireNonNull(adapter.get().load(request), "source result must not be null");
                loadedItems.addAll(result.items());
                limitations.addAll(result.limitations());
                successfulSources++;
            } catch (RuntimeException exception) {
                limitations.add(sourceType + " source could not be loaded; its results are unavailable.");
            }
        }

        if (successfulSources == 0) {
            limitations.add("All selected sources failed; review is unavailable.");
        }

        var items = loadedItems.stream()
            .sorted(Comparator.comparing(SourceItem::occurredAt).thenComparing(SourceItem::id))
            .toList();
        var coverage = successfulSources == 0 ? SourceCoverage.FAILED
            : limitations.isEmpty() ? SourceCoverage.COMPLETE : SourceCoverage.PARTIAL;

        return new SourceData(items, limitations, coverage);
    }
}

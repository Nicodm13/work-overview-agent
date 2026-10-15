package dk.school.workoverviewagent.source.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceCoverage;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

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
        for (var type : SourceType.values()) {
            var count = this.adapters.stream().filter(adapter -> adapter.sourceType() == type).count();
            if (count != 1) {
                throw new IllegalStateException("Expected exactly one source adapter for " + type + ", found " + count);
            }
        }
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
                limitations.add(failureLimitation(sourceType, exception));
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

    private static String failureLimitation(SourceType sourceType, RuntimeException exception) {
        var graphDeniedAccess = false;
        var cause = (Throwable) exception;
        for (var depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("AADSTS65001")) {
                return sourceType + " source requires Microsoft Graph consent. Ask a tenant administrator to review and grant the app's delegated Graph permissions.";
            }
            if (cause instanceof RestClientResponseException response && response.getStatusCode().value() == 403) {
                graphDeniedAccess = true;
            }
        }
        if (graphDeniedAccess) {
            return switch (sourceType) {
                case OUTLOOK -> "OUTLOOK source was denied by Microsoft Graph. Check Mail.Read delegated permission and consent, and the signed-in account's mailbox access.";
                case CALENDAR -> "CALENDAR source was denied by Microsoft Graph. Check Calendars.Read delegated permission and consent, and the signed-in account's calendar access.";
                case TEAMS -> "TEAMS source was denied by Microsoft Graph. Check Chat.Read delegated permission and consent, and the signed-in account's chat access.";
                case MEETING_NOTES -> "MEETING_NOTES source was denied by Microsoft Graph. Check Mail.Read delegated permission and consent, and the signed-in account's mailbox access.";
            };
        }
        return sourceType + " source could not be loaded; its results are unavailable.";
    }
}

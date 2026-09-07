package dk.school.workoverviewagent.source.filter;

import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SourceItemFilter {

    public List<SourceItem> matching(SourceRequest request, List<SourceItem> items) {
        return items.stream()
                .filter(item -> isInsideInterval(request, item))
                .filter(item -> matchesTextFilter(request, item))
                .toList();
    }

    private boolean isInsideInterval(SourceRequest request, SourceItem item) {
        if (item.occurredAt() == null) {
            return false;
        }
        if (request.startsAt() != null && item.occurredAt().isBefore(request.startsAt())) {
            return false;
        }
        return request.endsAt() == null || !item.occurredAt().isAfter(request.endsAt());
    }

    private boolean matchesTextFilter(SourceRequest request, SourceItem item) {
        if (request.filterText() == null || request.filterText().isBlank()) {
            return true;
        }
        var filterText = request.filterText().toLowerCase(Locale.ROOT);
        return contains(item.title(), filterText)
                || contains(item.content(), filterText)
                || contains(item.senderOrOrganizer(), filterText)
                || item.participants().stream().anyMatch(participant -> contains(participant, filterText))
                || item.attributes().values().stream().anyMatch(value -> contains(value, filterText));
    }

    private boolean contains(String value, String filterText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(filterText);
    }
}

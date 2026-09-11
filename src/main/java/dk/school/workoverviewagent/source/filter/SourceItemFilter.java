package dk.school.workoverviewagent.source.filter;

import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceItemFilter {

    public List<SourceItem> matching(SourceRequest request, List<SourceItem> items) {
        return items.stream()
            .filter(item -> isInsideInterval(request, item))
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

}

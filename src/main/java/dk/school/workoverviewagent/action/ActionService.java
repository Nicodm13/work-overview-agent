package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.review.contract.OverviewItem;
import dk.school.workoverviewagent.status.StatusItem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class ActionService implements IActionService {

    @Override
    public List<OverviewItem> addSuggestedActions(List<StatusItem> statusItems) {
        if (statusItems == null) {
            return List.of();
        }
        return statusItems.stream()
                .map(this::toOverviewItem)
                .toList();
    }

    private OverviewItem toOverviewItem(StatusItem statusItem) {
        var evidenceItem = statusItem.evidenceItem();
        return new OverviewItem(
                evidenceItem.id(),
                evidenceItem.title(),
                evidenceItem.summary(),
                "UNRANKED",
                evidenceItem.evidenceStatus(),
                evidenceItem.references(),
                statusItem.workStatus(),
                statusItem.statusSource(),
                "Review the source evidence and decide whether follow-up is needed.");
    }
}

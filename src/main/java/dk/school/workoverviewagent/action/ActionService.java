package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.review.contract.OverviewItem;
import dk.school.workoverviewagent.status.StatusItem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class ActionService implements IActionService {

    @Override
    public List<OverviewItem> addSuggestedActions(String userId, List<StatusItem> statusItems) {
        // TODO #11: Suggest next actions and prepare editable drafts without executing them.
        return null;
    }
}

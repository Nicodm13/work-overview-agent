package dk.school.workoverviewagent.action.api;

import dk.school.workoverviewagent.review.contract.OverviewItem;
import dk.school.workoverviewagent.status.StatusItem;
import java.util.List;

public interface IActionService {

    List<OverviewItem> addSuggestedActions(String userId, List<StatusItem> statusItems);
}

package dk.school.workoverviewagent.action.api;

import dk.school.workoverviewagent.model.StatusItem;
import dk.school.workoverviewagent.review.contract.OverviewItem;
import java.util.List;

public interface IActionService {

    List<OverviewItem> addSuggestedActions(List<StatusItem> statusItems);
}

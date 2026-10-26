package dk.school.workoverviewagent.action.api;

import dk.school.workoverviewagent.model.ActionDeliveryResult;
import dk.school.workoverviewagent.model.ActionDraft;

public interface IActionDelivery {

    ActionDeliveryResult deliver(ActionDraft draft);
}

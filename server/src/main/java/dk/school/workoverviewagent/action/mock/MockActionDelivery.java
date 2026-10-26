package dk.school.workoverviewagent.action.mock;

import dk.school.workoverviewagent.action.api.IActionDelivery;
import dk.school.workoverviewagent.model.ActionDeliveryResult;
import dk.school.workoverviewagent.model.ActionDeliveryStatus;
import dk.school.workoverviewagent.model.ActionDraft;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "mock")
class MockActionDelivery implements IActionDelivery {

    @Override
    public ActionDeliveryResult deliver(ActionDraft draft) {
        return new ActionDeliveryResult(ActionDeliveryStatus.SIMULATED, "mock:" + draft.id());
    }
}

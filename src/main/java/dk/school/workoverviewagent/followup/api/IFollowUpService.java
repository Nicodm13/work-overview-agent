package dk.school.workoverviewagent.followup.api;

import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.model.FollowUpItem;
import java.util.List;

public interface IFollowUpService {

    FollowUpItem createFollowUpItem(CreateFollowUpItemRequest request);

    FollowUpItem getFollowUpItem(String ownerId, String followUpItemId);

    FollowUpItem attachEvidence(AttachEvidenceToFollowUpRequest request);

    List<FollowUpItem> listFollowUpItems(String ownerId);
}

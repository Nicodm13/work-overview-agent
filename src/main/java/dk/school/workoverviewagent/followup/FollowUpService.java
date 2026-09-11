package dk.school.workoverviewagent.followup;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.model.EvidenceReference;
import dk.school.workoverviewagent.model.FollowUpItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stores explicitly requested follow-up items and their evidence links. This service does not
 * interpret source content or decide which evidence belongs together.
 */
@Component
class FollowUpService implements IFollowUpService {

    private final Map<String, FollowUpItem> followUpItemsById = new LinkedHashMap<>();

    @Override
    public synchronized FollowUpItem createFollowUpItem(CreateFollowUpItemRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateTitle(request.title());

        var item = new FollowUpItem(
                UUID.randomUUID().toString(),
                request.title(),
                request.summary() == null ? "" : request.summary(),
                request.evidenceReferences());
        followUpItemsById.put(item.id(), item);
        return item;
    }

    @Override
    public synchronized FollowUpItem getFollowUpItem(String followUpItemId) {
        validateFollowUpItemId(followUpItemId);
        var item = followUpItemsById.get(followUpItemId);
        if (item == null) {
            throw new IllegalArgumentException("follow-up item not found: " + followUpItemId);
        }
        return item;
    }

    @Override
    public synchronized FollowUpItem attachEvidence(AttachEvidenceToFollowUpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.evidenceReference(), "evidenceReference must not be null");
        var item = getFollowUpItem(request.followUpItemId());
        var references = new ArrayList<>(item.evidenceReferences());
        if (!references.contains(request.evidenceReference())) {
            references.add(request.evidenceReference());
        }
        var updatedItem = new FollowUpItem(item.id(), item.title(), item.summary(), references);
        followUpItemsById.put(updatedItem.id(), updatedItem);
        return updatedItem;
    }

    @Override
    public synchronized List<FollowUpItem> listFollowUpItems() {
        return List.copyOf(followUpItemsById.values());
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }

    private void validateFollowUpItemId(String followUpItemId) {
        if (followUpItemId == null || followUpItemId.isBlank()) {
            throw new IllegalArgumentException("followUpItemId must not be blank");
        }
    }
}

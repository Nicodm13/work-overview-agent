package dk.school.workoverviewagent.followup;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.model.FollowUpItem;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stores explicitly requested follow-up items and their evidence links. This service does not
 * interpret source content or decide which evidence belongs together.
 */
@Component
class FollowUpService implements IFollowUpService {

    private final Map<String, FollowUpItem> followUpItemsByOwnerAndId = new LinkedHashMap<>();

    @Override
    public synchronized FollowUpItem createFollowUpItem(CreateFollowUpItemRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateTitle(request.title());

        var item = new FollowUpItem(
            UUID.randomUUID().toString(),
            requiredOwnerId(request.ownerId()),
            request.title(),
            request.summary() == null ? "" : request.summary(),
            request.evidenceReferences());
        followUpItemsByOwnerAndId.put(key(item.ownerId(), item.id()), item);
        return item;
    }

    @Override
    public synchronized FollowUpItem getFollowUpItem(String ownerId, String followUpItemId) {
        requiredOwnerId(ownerId);
        validateFollowUpItemId(followUpItemId);
        var item = followUpItemsByOwnerAndId.get(key(ownerId, followUpItemId));
        if (item == null) {
            throw new IllegalArgumentException("follow-up item not found: " + followUpItemId);
        }
        return item;
    }

    @Override
    public synchronized FollowUpItem attachEvidence(AttachEvidenceToFollowUpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.evidenceReference(), "evidenceReference must not be null");
        var item = getFollowUpItem(request.ownerId(), request.followUpItemId());
        var references = new ArrayList<>(item.evidenceReferences());
        if (!references.contains(request.evidenceReference())) {
            references.add(request.evidenceReference());
        }
        var updatedItem = new FollowUpItem(item.id(), item.ownerId(), item.title(), item.summary(), references);
        followUpItemsByOwnerAndId.put(key(updatedItem.ownerId(), updatedItem.id()), updatedItem);
        return updatedItem;
    }

    @Override
    public synchronized List<FollowUpItem> listFollowUpItems(String ownerId) {
        requiredOwnerId(ownerId);
        return followUpItemsByOwnerAndId.values().stream().filter(item -> item.ownerId().equals(ownerId)).toList();
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

    private String requiredOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return ownerId;
    }

    private String key(String ownerId, String followUpItemId) {
        return ownerId + ':' + followUpItemId;
    }
}

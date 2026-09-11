package dk.school.workoverviewagent.followup;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.workoverviewagent.followup.contract.AttachEvidenceToFollowUpRequest;
import dk.school.workoverviewagent.followup.contract.CreateFollowUpItemRequest;
import dk.school.workoverviewagent.followup.repository.IFollowUpRepository;
import dk.school.workoverviewagent.evidence.repository.IEvidenceRepository;
import dk.school.workoverviewagent.model.FollowUpItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores explicitly requested follow-up items and their evidence links. This service does not
 * interpret source content or decide which evidence belongs together.
 */
@Component
class FollowUpService implements IFollowUpService {

    private final IFollowUpRepository followUpRepository;
    private final IEvidenceRepository evidenceRepository;

    FollowUpService(
        IFollowUpRepository followUpRepository,
        IEvidenceRepository evidenceRepository) {
        this.followUpRepository = followUpRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    public FollowUpItem createFollowUpItem(CreateFollowUpItemRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateTitle(request.title());

        var item = new FollowUpItem(
            UUID.randomUUID().toString(),
            requiredOwnerId(request.ownerId()),
            request.title(),
            request.summary() == null ? "" : request.summary(),
            request.evidenceReferences());
        followUpRepository.save(item.ownerId(), item);
        item.evidenceReferences().forEach(reference -> {
            evidenceRepository.save(item.ownerId(), reference);
            followUpRepository.linkEvidenceReference(item.ownerId(), item.id(), reference.id());
        });
        return item;
    }

    @Override
    public FollowUpItem getFollowUpItem(String ownerId, String followUpItemId) {
        requiredOwnerId(ownerId);
        validateFollowUpItemId(followUpItemId);
        return followUpRepository.findById(ownerId, followUpItemId)
            .orElseThrow(() -> new IllegalArgumentException("follow-up item not found: " + followUpItemId));
    }

    @Override
    public FollowUpItem attachEvidence(AttachEvidenceToFollowUpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.evidenceReference(), "evidenceReference must not be null");
        var item = getFollowUpItem(request.ownerId(), request.followUpItemId());
        evidenceRepository.save(item.ownerId(), request.evidenceReference());
        followUpRepository.linkEvidenceReference(
            item.ownerId(),
            item.id(),
            request.evidenceReference().id());
        return getFollowUpItem(item.ownerId(), item.id());
    }

    @Override
    public List<FollowUpItem> listFollowUpItems(String ownerId) {
        requiredOwnerId(ownerId);
        return followUpRepository.findAll(ownerId).stream()
            .map(item -> getFollowUpItem(ownerId, item.id()))
            .toList();
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

}

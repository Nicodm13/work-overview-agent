package dk.school.workoverviewagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogEntry(
    String id,
    String ownerId,
    String followUpItemId,
    String actionType,
    Instant timestamp,
    String approvalStatus,
    String approvedContentReference,
    String deliveryStatus,
    @Nullable @JsonProperty(required = false) String externalReference,
    @Nullable @JsonProperty(required = false) String failureDetail) {
}

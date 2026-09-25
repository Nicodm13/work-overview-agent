package dk.school.workoverviewagent.graph;

import java.util.List;

public class GraphConsentRequiredException extends IllegalStateException {

    private final List<GraphDelegatedPermission> requiredPermissions;

    public GraphConsentRequiredException(
        List<GraphDelegatedPermission> requiredPermissions,
        Throwable cause) {
        super("Microsoft 365 access requires user or administrator consent for the configured delegated permissions.", cause);
        this.requiredPermissions = List.copyOf(requiredPermissions);
    }

    public List<GraphDelegatedPermission> requiredPermissions() {
        return requiredPermissions;
    }
}

package dk.school.workoverviewagent.graph;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
class GraphConsentRequirements implements IGraphConsentRequirements {

    private static final List<GraphDelegatedPermission> REQUIRED_DELEGATED_PERMISSIONS = List.of(
        GraphDelegatedPermission.MAIL_READ,
        GraphDelegatedPermission.CALENDARS_READ,
        GraphDelegatedPermission.TEAMS_CHAT_READ,
        GraphDelegatedPermission.TEAMS_CHANNEL_MESSAGE_READ,
        GraphDelegatedPermission.ONENOTE_READ);

    @Override
    public List<GraphDelegatedPermission> requiredDelegatedPermissions() {
        return REQUIRED_DELEGATED_PERMISSIONS;
    }
}

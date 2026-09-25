package dk.school.workoverviewagent.graph;

public enum GraphDelegatedPermission {

    MAIL_READ("Mail.Read", false),
    CALENDARS_READ("Calendars.Read", false),
    TEAMS_CHAT_READ("Chat.Read", false),
    TEAMS_CHANNEL_MESSAGE_READ("ChannelMessage.Read.All", true),
    ONENOTE_READ("Notes.Read", false);

    private final String scope;
    private final boolean requiresAdminConsent;

    GraphDelegatedPermission(String scope, boolean requiresAdminConsent) {
        this.scope = scope;
        this.requiresAdminConsent = requiresAdminConsent;
    }

    public String scope() {
        return scope;
    }

    public boolean requiresAdminConsent() {
        return requiresAdminConsent;
    }
}

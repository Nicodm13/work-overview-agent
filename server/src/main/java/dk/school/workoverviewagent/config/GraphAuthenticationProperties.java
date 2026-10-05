package dk.school.workoverviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("work-overview.graph.authentication")
public record GraphAuthenticationProperties(
    String clientId,
    String clientSecret,
    String certificatePath,
    String certificatePassword) {

    public String requiredClientId() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("WORK_OVERVIEW_ENTRA_CLIENT_ID must be configured when MCP security is enabled");
        }
        return clientId;
    }

    public boolean usesCertificate() {
        return certificatePath != null && !certificatePath.isBlank();
    }

    public void validateCredentialConfiguration() {
        if (usesCertificate() == (clientSecret != null && !clientSecret.isBlank())) {
            throw new IllegalStateException(
                "Configure exactly one of WORK_OVERVIEW_ENTRA_CLIENT_SECRET or WORK_OVERVIEW_ENTRA_CERTIFICATE_PATH");
        }
    }
}

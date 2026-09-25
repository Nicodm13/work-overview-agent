package dk.school.workoverviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("work-overview.security")
public record McpSecurityProperties(boolean enabled, String issuerUri, String audience) {

    public String requiredIssuerUri() {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException(
                "WORK_OVERVIEW_ENTRA_ISSUER_URI must be configured when MCP security is enabled");
        }
        return issuerUri;
    }

    public String requiredAudience() {
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException(
                "WORK_OVERVIEW_ENTRA_AUDIENCE must be configured when MCP security is enabled");
        }
        return audience;
    }
}

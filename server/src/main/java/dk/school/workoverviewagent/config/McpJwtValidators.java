package dk.school.workoverviewagent.config;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

final class McpJwtValidators {

    private McpJwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> create(McpSecurityProperties properties) {
        var issuerUri = properties.requiredIssuerUri();
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            audienceValidator(properties.requiredAudience()),
            tenantValidator(properties.requiredTenantId()));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The token was not issued for this MCP server", null));
    }

    private static OAuth2TokenValidator<Jwt> tenantValidator(String tenantId) {
        return jwt -> tenantId.equals(jwt.getClaimAsString("tid"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The token was not issued by the configured Entra tenant", null));
    }
}

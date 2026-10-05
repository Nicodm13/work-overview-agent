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
            tenantValidator(properties.requiredTenantId()),
            scopeValidator("access_as_user"));
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

    private static OAuth2TokenValidator<Jwt> scopeValidator(String requiredScope) {
        return jwt -> jwt.getClaimAsString("scp") != null
            && java.util.Arrays.asList(jwt.getClaimAsString("scp").split(" ")).contains(requiredScope)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "insufficient_scope", "The token does not grant access to the MCP server", null));
    }
}

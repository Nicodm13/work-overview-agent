package dk.school.workoverviewagent.graph;

import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.identity.OnBehalfOfCredentialBuilder;
import dk.school.workoverviewagent.config.GraphAuthenticationProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class GraphAccessTokenProvider implements IGraphAccessTokenProvider {

    private static final String GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

    private final GraphAuthenticationProperties properties;
    private final String tenantId;
    private final IGraphConsentRequirements consentRequirements;

    public GraphAccessTokenProvider(
        GraphAuthenticationProperties properties,
        String tenantId,
        IGraphConsentRequirements consentRequirements) {
        this.properties = properties;
        this.tenantId = tenantId;
        this.consentRequirements = consentRequirements;
    }

    @Override
    public String getAccessToken() {
        var credential = new OnBehalfOfCredentialBuilder()
            .tenantId(tenantId)
            .clientId(properties.requiredClientId())
            .userAssertion(currentUserAssertion());

        configureClientCredential(credential);
        try {
            return credential.build()
                .getTokenSync(new TokenRequestContext().addScopes(GRAPH_DEFAULT_SCOPE))
                .getToken();
        } catch (ClientAuthenticationException exception) {
            if (GraphConsentErrorClassifier.requiresConsent(exception.getMessage())) {
                throw new GraphConsentRequiredException(
                    consentRequirements.requiredDelegatedPermissions(), exception);
            }
            throw exception;
        }
    }

    private void configureClientCredential(OnBehalfOfCredentialBuilder credential) {
        if (!properties.usesCertificate()) {
            credential.clientSecret(properties.clientSecret());
            return;
        }

        if (properties.certificatePath().endsWith(".pfx") || properties.certificatePath().endsWith(".p12")) {
            credential.pfxCertificate(properties.certificatePath());
            if (properties.certificatePassword() != null && !properties.certificatePassword().isBlank()) {
                credential.clientCertificatePassword(properties.certificatePassword());
            }
            return;
        }

        credential.pemCertificate(properties.certificatePath());
    }

    private String currentUserAssertion() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("An authenticated Entra JWT is required to call Microsoft Graph");
        }
        return jwt.getTokenValue();
    }

}

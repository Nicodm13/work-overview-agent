package dk.school.workoverviewagent.graph;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.OnBehalfOfCredential;
import com.azure.identity.OnBehalfOfCredentialBuilder;
import dk.school.workoverviewagent.config.GraphAuthenticationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class GraphAccessTokenProvider implements IGraphAccessTokenProvider {

    private static final String GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";
    private static final int MAX_CACHED_CREDENTIALS = 256;
    private static final Duration MAX_CREDENTIAL_LIFETIME = Duration.ofHours(2);

    private final GraphAuthenticationProperties properties;
    private final String tenantId;
    private final Map<String, CachedCredential> credentials = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedCredential> eldest) {
            return size() > MAX_CACHED_CREDENTIALS;
        }
    };

    public GraphAccessTokenProvider(GraphAuthenticationProperties properties, String tenantId) {
        this.properties = properties;
        this.tenantId = tenantId;
    }

    @Override
    public String getAccessToken() {
        return credentialFor(currentUserJwt())
            .getTokenSync(new TokenRequestContext().addScopes(GRAPH_DEFAULT_SCOPE))
            .getToken();
    }

    private synchronized OnBehalfOfCredential credentialFor(Jwt jwt) {
        var now = Instant.now();
        credentials.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        var key = assertionHash(jwt.getTokenValue());
        var cached = credentials.get(key);
        if (cached != null) {
            return cached.credential();
        }

        var credential = new OnBehalfOfCredentialBuilder()
            .tenantId(tenantId)
            .clientId(properties.requiredClientId())
            .userAssertion(jwt.getTokenValue());

        configureClientCredential(credential);
        var expiresAt = now.plus(MAX_CREDENTIAL_LIFETIME);
        if (jwt.getExpiresAt() != null && jwt.getExpiresAt().isBefore(expiresAt)) {
            expiresAt = jwt.getExpiresAt();
        }
        var built = credential.build();
        if (expiresAt.isAfter(now)) {
            credentials.put(key, new CachedCredential(built, expiresAt));
        }
        return built;
    }

    private String assertionHash(String assertion) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(assertion.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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

    private Jwt currentUserJwt() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("An authenticated Entra JWT is required to call Microsoft Graph");
        }
        return jwt;
    }

    private record CachedCredential(OnBehalfOfCredential credential, Instant expiresAt) {
    }
}

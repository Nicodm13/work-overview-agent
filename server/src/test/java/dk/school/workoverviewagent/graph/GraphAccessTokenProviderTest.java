package dk.school.workoverviewagent.graph;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.OnBehalfOfCredential;
import com.azure.identity.OnBehalfOfCredentialBuilder;
import dk.school.workoverviewagent.config.GraphAuthenticationProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphAccessTokenProviderTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesTheIncomingMcpTokenAsTheGraphUserAssertion() {
        var jwt = Jwt.withTokenValue("mcp-access-token")
            .header("alg", "none")
            .claim("tid", "tenant-a")
            .claim("oid", "object-a")
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        var graphCredential = mock(OnBehalfOfCredential.class);
        when(graphCredential.getTokenSync(any(TokenRequestContext.class)))
            .thenReturn(new AccessToken("graph-access-token", OffsetDateTime.now().plusHours(1)));

        try (MockedConstruction<OnBehalfOfCredentialBuilder> builders = mockConstruction(
            OnBehalfOfCredentialBuilder.class,
            (builder, context) -> {
                when(builder.tenantId("tenant-a")).thenReturn(builder);
                when(builder.clientId("client-id")).thenReturn(builder);
                when(builder.userAssertion("mcp-access-token")).thenReturn(builder);
                when(builder.clientSecret("client-secret")).thenReturn(builder);
                when(builder.build()).thenReturn(graphCredential);
            })) {
            var provider = new GraphAccessTokenProvider(
                new GraphAuthenticationProperties("client-id", "client-secret", null, null),
                "tenant-a");

            assertThat(provider.getAccessToken()).isEqualTo("graph-access-token");

            var builder = builders.constructed().getFirst();
            verify(builder).userAssertion("mcp-access-token");
            var request = ArgumentCaptor.forClass(TokenRequestContext.class);
            verify(graphCredential).getTokenSync(request.capture());
            assertThat(request.getValue().getScopes()).containsExactly("https://graph.microsoft.com/.default");
        }
    }

    @Test
    void reusesTheCredentialForRepeatedRequestsWithTheSameAssertion() {
        authenticate("mcp-access-token");
        var graphCredential = mock(OnBehalfOfCredential.class);
        when(graphCredential.getTokenSync(any(TokenRequestContext.class)))
            .thenReturn(new AccessToken("graph-access-token", OffsetDateTime.now().plusHours(1)));

        try (MockedConstruction<OnBehalfOfCredentialBuilder> builders = mockConstruction(
            OnBehalfOfCredentialBuilder.class,
            (builder, context) -> configureBuilder(builder, graphCredential))) {
            var provider = provider();

            assertThat(provider.getAccessToken()).isEqualTo("graph-access-token");
            assertThat(provider.getAccessToken()).isEqualTo("graph-access-token");

            assertThat(builders.constructed()).hasSize(1);
            verify(graphCredential, times(2)).getTokenSync(any(TokenRequestContext.class));
        }
    }

    @Test
    void keepsCredentialsForDifferentUserAssertionsSeparate() {
        var firstCredential = mock(OnBehalfOfCredential.class);
        var secondCredential = mock(OnBehalfOfCredential.class);
        when(firstCredential.getTokenSync(any(TokenRequestContext.class)))
            .thenReturn(new AccessToken("first-graph-token", OffsetDateTime.now().plusHours(1)));
        when(secondCredential.getTokenSync(any(TokenRequestContext.class)))
            .thenReturn(new AccessToken("second-graph-token", OffsetDateTime.now().plusHours(1)));

        try (MockedConstruction<OnBehalfOfCredentialBuilder> builders = mockConstruction(
            OnBehalfOfCredentialBuilder.class,
            (builder, context) -> configureBuilder(builder,
                context.getCount() == 1 ? firstCredential : secondCredential))) {
            var provider = provider();

            authenticate("first-mcp-token");
            assertThat(provider.getAccessToken()).isEqualTo("first-graph-token");
            authenticate("second-mcp-token");
            assertThat(provider.getAccessToken()).isEqualTo("second-graph-token");
            authenticate("first-mcp-token");
            assertThat(provider.getAccessToken()).isEqualTo("first-graph-token");

            assertThat(builders.constructed()).hasSize(2);
            verify(builders.constructed().get(0)).userAssertion("first-mcp-token");
            verify(builders.constructed().get(1)).userAssertion("second-mcp-token");
        }
    }

    @Test
    void doesNotRetainCredentialsBeyondTheAssertionExpiry() {
        authenticate("expired-mcp-token", Instant.now().minusSeconds(1));
        var graphCredential = mock(OnBehalfOfCredential.class);
        when(graphCredential.getTokenSync(any(TokenRequestContext.class)))
            .thenReturn(new AccessToken("graph-access-token", OffsetDateTime.now().plusHours(1)));

        try (MockedConstruction<OnBehalfOfCredentialBuilder> builders = mockConstruction(
            OnBehalfOfCredentialBuilder.class,
            (builder, context) -> configureBuilder(builder, graphCredential))) {
            var provider = provider();

            provider.getAccessToken();
            provider.getAccessToken();

            assertThat(builders.constructed()).hasSize(2);
        }
    }

    private GraphAccessTokenProvider provider() {
        return new GraphAccessTokenProvider(
            new GraphAuthenticationProperties("client-id", "client-secret", null, null),
            "tenant-a");
    }

    private void authenticate(String token) {
        authenticate(token, Instant.now().plusSeconds(3600));
    }

    private void authenticate(String token, Instant expiresAt) {
        var jwt = Jwt.withTokenValue(token)
            .header("alg", "none")
            .claim("tid", "tenant-a")
            .claim("oid", token)
            .expiresAt(expiresAt)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private void configureBuilder(OnBehalfOfCredentialBuilder builder, OnBehalfOfCredential credential) {
        when(builder.tenantId("tenant-a")).thenReturn(builder);
        when(builder.clientId("client-id")).thenReturn(builder);
        when(builder.userAssertion(any(String.class))).thenReturn(builder);
        when(builder.clientSecret("client-secret")).thenReturn(builder);
        when(builder.build()).thenReturn(credential);
    }
}

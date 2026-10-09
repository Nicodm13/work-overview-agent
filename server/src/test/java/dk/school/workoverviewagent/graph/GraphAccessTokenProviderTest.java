package dk.school.workoverviewagent.graph;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.OnBehalfOfCredential;
import com.azure.identity.OnBehalfOfCredentialBuilder;
import dk.school.workoverviewagent.config.GraphAuthenticationProperties;
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
}

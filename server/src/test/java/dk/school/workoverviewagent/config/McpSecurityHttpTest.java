package dk.school.workoverviewagent.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = McpSecurityTestEndpoint.class)
@Import(McpSecurityConfiguration.class)
class McpSecurityHttpTest {

    private static final HttpServer OPENID_SERVER = startOpenIdServer();
    private static final String ISSUER = "http://127.0.0.1:" + OPENID_SERVER.getAddress().getPort() + "/issuer";
    private static final String AUDIENCE = "work-overview-client-id";
    private static final String SCOPE = "https://localhost:8080/mcp/access_as_user";
    private static final RSAKey SIGNING_KEY = generateKey();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("work-overview.security.enabled", () -> true);
        registry.add("work-overview.security.issuer-uri", () -> ISSUER);
        registry.add("work-overview.security.audience", () -> AUDIENCE);
        registry.add("work-overview.security.scope", () -> SCOPE);
        registry.add("work-overview.security.tenant-id", () -> "tenant-a");
        registry.add("work-overview.security.resource-uri", () -> "https://localhost:8080/mcp");
    }

    @AfterAll
    static void stopOpenIdServer() {
        OPENID_SERVER.stop(0);
    }

    @Test
    void rejectsMcpRequestsWithoutABearerToken() throws Exception {
        mockMvc.perform(get("/mcp"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publishesEntraProtectedResourceMetadata() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "resource":"https://localhost:8080/mcp",
                  "authorization_servers":["%s"],
                  "scopes_supported":["%s"],
                  "bearer_methods_supported":["header"],
                  "tls_client_certificate_bound_access_tokens":false
                }
                """.formatted(ISSUER, SCOPE), false));
    }

    @Test
    void rejectsATokenForAnotherAudience() throws Exception {
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(SIGNING_KEY, "other-api", "tenant-a", Instant.now().plusSeconds(60))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenWithoutTheAccessAsUserScope() throws Exception {
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(
            SIGNING_KEY, AUDIENCE, "tenant-a", Instant.now().plusSeconds(60), "User.Read")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenFromAnotherTenant() throws Exception {
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(SIGNING_KEY, AUDIENCE, "tenant-b", Instant.now().plusSeconds(60))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredAndInvalidlySignedTokens() throws Exception {
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(SIGNING_KEY, AUDIENCE, "tenant-a", Instant.now().minusSeconds(60))))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(generateKey(), AUDIENCE, "tenant-a", Instant.now().plusSeconds(60))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAValidTokenForTheMcpEndpoint() throws Exception {
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(SIGNING_KEY, AUDIENCE, "tenant-a", Instant.now().plusSeconds(60))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string("reachable"));
    }

    private static String token(RSAKey key, String audience, String tenantId, Instant expiresAt) throws JOSEException {
        return token(key, audience, tenantId, expiresAt, "access_as_user");
    }

    private static String token(RSAKey key, String audience, String tenantId, Instant expiresAt, String scopes)
            throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject("subject-a")
            .audience(audience)
            .claim("tid", tenantId)
            .claim("oid", "object-a")
            .claim("scp", scopes)
            .issueTime(new Date())
            .expirationTime(Date.from(expiresAt))
            .build();
        var jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key.toPrivateKey()));
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not generate test signing key", exception);
        }
    }

    private static HttpServer startOpenIdServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                var response = exchange.getRequestURI().getPath().contains("openid-configuration")
                    ? "{\"issuer\":\"" + ISSUER + "\",\"jwks_uri\":\"" + ISSUER.replace("/issuer", "/jwks") + "\"}"
                    : "{\"keys\":[" + SIGNING_KEY.toPublicJWK().toJSONString() + "]}";
                var bytes = response.getBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start local OpenID test server", exception);
        }
    }

}

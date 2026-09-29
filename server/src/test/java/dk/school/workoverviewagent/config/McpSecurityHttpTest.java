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
    private static final String AUDIENCE = "api://work-overview-agent";
    private static final RSAKey SIGNING_KEY = generateKey();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("work-overview.security.enabled", () -> true);
        registry.add("work-overview.security.issuer-uri", () -> ISSUER);
        registry.add("work-overview.security.audience", () -> AUDIENCE);
        registry.add("work-overview.security.tenant-id", () -> "tenant-a");
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
    void rejectsATokenForAnotherAudience() throws Exception {
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer " + token(SIGNING_KEY, "other-api", "tenant-a", Instant.now().plusSeconds(60))))
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
        var claims = new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject("subject-a")
            .audience(audience)
            .claim("tid", tenantId)
            .claim("oid", "object-a")
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

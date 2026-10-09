package dk.school.workoverviewagent.config;

import dk.school.workoverviewagent.followup.api.IFollowUpService;
import dk.school.mcptest.McpAuthenticationTestApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = McpAuthenticationTestApplication.class,
    properties = {
        "work-overview.security.enabled=true",
        "work-overview.security.issuer-uri=https://login.example.test/tenant-a/v2.0",
        "work-overview.security.audience=work-overview-client-id",
        "work-overview.security.scope=https://localhost:8080/mcp/access_as_user",
        "work-overview.security.tenant-id=tenant-a",
        "work-overview.security.resource-uri=https://localhost:8080/mcp"
    })
@AutoConfigureMockMvc
@Import(McpSecurityConfiguration.class)
class McpAuthenticatedToolHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IFollowUpService followUpService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedMcpToolReceivesTheCurrentTenantAndUser() throws Exception {
        when(followUpService.listFollowUpItems(anyString())).thenReturn(List.of());
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("tid", "tenant-a")
                .claim("oid", token)
                .claim("scp", "access_as_user")
                .build();
        });

        callListFollowUpItems("object-a");
        callListFollowUpItems("object-b");

        verify(followUpService).listFollowUpItems("tenant-a:object-a");
        verify(followUpService).listFollowUpItems("tenant-a:object-b");
    }

    private void callListFollowUpItems(String userId) throws Exception {
        var sessionId = initialize(userId);
        var result = mockMvc.perform(post("/mcp")
                .header("Authorization", "Bearer " + userId)
                .header("Mcp-Session-Id", sessionId)
                .header("MCP-Protocol-Version", "2025-03-26")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_follow_up_items","arguments":{}}}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"result\"");
    }

    private String initialize(String userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/mcp")
                .header("Authorization", "Bearer " + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"security-test","version":"1.0"}}}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getHeader("Mcp-Session-Id");
    }

}

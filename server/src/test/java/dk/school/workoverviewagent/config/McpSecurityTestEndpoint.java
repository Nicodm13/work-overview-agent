package dk.school.workoverviewagent.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpSecurityTestEndpoint {

    @GetMapping("/mcp")
    String getMcp() {
        return "reachable";
    }
}

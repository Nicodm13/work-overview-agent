package dk.school.workoverviewagent.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class EvidenceTools {

    @McpTool(
        name = "get_item_evidence",
        description = "Return the source references that document a selected evidence item.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public void getItemEvidence() {
        // TODO: Delegate to EvidenceService after the evidence MCP boundary is implemented.
        throw new UnsupportedOperationException("get_item_evidence is not implemented yet");
    }
}

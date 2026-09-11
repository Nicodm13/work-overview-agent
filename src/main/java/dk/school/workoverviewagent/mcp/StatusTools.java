package dk.school.workoverviewagent.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class StatusTools {

    @McpTool(
        name = "get_status",
        description = "Return the current user-confirmed work status and status history.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public void getStatus() {
        // TODO: Create GetWorkStatusRequest and delegate to StatusService.
        throw new UnsupportedOperationException("get_status is not implemented yet");
    }

    @McpTool(
        name = "update_status",
        description = "Store an explicit user-confirmed work status. This does not infer completion from evidence.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public void updateStatus() {
        // TODO: Build UpdateWorkStatusRequest with USER_CONFIRMED status source and delegate to StatusService.
        throw new UnsupportedOperationException("update_status is not implemented yet");
    }
}

package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.model.StatusSource;
import dk.school.workoverviewagent.model.WorkStatus;
import dk.school.workoverviewagent.status.api.IStatusService;
import dk.school.workoverviewagent.status.contract.GetWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.GetWorkStatusResponse;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusRequest;
import dk.school.workoverviewagent.status.contract.UpdateWorkStatusResponse;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class StatusTools {

    private final IStatusService statusService;
    private final IUserProvider userProvider;

    public StatusTools(IStatusService statusService, IUserProvider userProvider) {
        this.statusService = statusService;
        this.userProvider = userProvider;
    }

    @McpTool(
        name = "get_status",
        description = "Return the current user-confirmed work status and status history.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public GetWorkStatusResponse getStatus(
        @McpToolParam(description = "ID of the follow-up item whose work status should be retrieved.", required = true)
        String followUpItemId) {
        return statusService.getWorkStatus(new GetWorkStatusRequest(
            userProvider.getUserId(),
            followUpItemId));
    }

    @McpTool(
        name = "update_status",
        description = "Store an explicit user-confirmed work status. This does not infer completion from evidence.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false))
    public UpdateWorkStatusResponse updateStatus(
        @McpToolParam(description = "ID of the follow-up item whose user-confirmed status should be updated.", required = true)
        String followUpItemId,
        @McpToolParam(description = "User-confirmed work status to store.", required = true)
        WorkStatus workStatus,
        @McpToolParam(description = "Optional explanation supplied or confirmed by the user.", required = false)
        String reason) {
        return statusService.updateWorkStatus(new UpdateWorkStatusRequest(
            userProvider.getUserId(),
            followUpItemId,
            workStatus,
            reason == null ? "" : reason,
            StatusSource.USER_CONFIRMED,
            null));
    }
}

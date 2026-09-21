package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.evidence.api.IEvidenceService;
import dk.school.workoverviewagent.evidence.contract.EvidenceResponse;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EvidenceTools {

    private final IEvidenceService evidenceService;
    private final IUserProvider userProvider;

    public EvidenceTools(IEvidenceService evidenceService, IUserProvider userProvider) {
        this.evidenceService = evidenceService;
        this.userProvider = userProvider;
    }

    @McpTool(
        name = "get_item_evidence",
        description = "Return the minimized source reference for a selected evidence reference. The result documents digital evidence and does not determine work status.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public EvidenceResponse getItemEvidence(
        @McpToolParam(description = "ID of the evidence reference returned by get_review.", required = true)
        String evidenceReferenceId) {
        return evidenceService.getEvidence(userProvider.getUserId(), evidenceReferenceId);
    }
}

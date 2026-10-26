package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.ContactLookupService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContactTools {

    private final ContactLookupService contactLookupService;

    public ContactTools(ContactLookupService contactLookupService) {
        this.contactLookupService = contactLookupService;
    }

    @McpTool(
        name = "find_contacts",
        description = "Search the configured contact directory and return display name, email address, user principal name, and Graph ID for resolving email or Teams recipients. Graph mode searches the current tenant; mock mode searches mock-data/users.csv. Supports partial names and email addresses. Results can be ambiguous; do not guess when multiple people match.",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public List<Contact> findContacts(
        @McpToolParam(description = "A person's full or partial name or email address.", required = true)
        String query) {
        return contactLookupService.findContacts(query);
    }
}

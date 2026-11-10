package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.action.api.IActionService;
import dk.school.workoverviewagent.user.IUserProvider;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionToolsAuditTest {

    @Test
    void retrievesAuditEntriesForTheAuthenticatedUser() {
        var service = mock(IActionService.class);
        var userProvider = mock(IUserProvider.class);
        when(userProvider.getUserId()).thenReturn("authenticated-user");

        new ActionTools(service, userProvider).getActionAuditLog();

        verify(service).auditLog("authenticated-user");
    }
}

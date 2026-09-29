package dk.school.workoverviewagent.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class UserProviderTest {

    private final UserProvider userProvider = new UserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void derivesTheOwnerIdFromTenantAndObjectIdClaims() {
        var jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("tid", "tenant-a")
            .claim("oid", "object-a")
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(userProvider.getUserId()).isEqualTo("tenant-a:object-a");
    }

    @Test
    void rejectsAnUnauthenticatedContext() {
        assertThatThrownBy(userProvider::getUserId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("authenticated Entra JWT");
    }
}

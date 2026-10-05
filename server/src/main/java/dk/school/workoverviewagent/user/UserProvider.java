package dk.school.workoverviewagent.user;

import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Component
class UserProvider implements IUserProvider {

    @Override
    public String getUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("An authenticated Entra JWT is required to identify the current user");
        }
        return requiredClaim(jwt, "tid") + ':' + requiredClaim(jwt, "oid");
    }

    private String requiredClaim(Jwt jwt, String claimName) {
        var value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Authenticated Entra JWT is missing required claim: " + claimName);
        }
        return value;
    }
}

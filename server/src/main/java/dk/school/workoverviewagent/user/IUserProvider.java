package dk.school.workoverviewagent.user;

public interface IUserProvider {

    String getUserId();

    default String getUserPrincipalName() {
        throw new IllegalStateException("The authenticated user email or UPN is not available");
    }
}

package dk.school.workoverviewagent.user;

import org.springframework.stereotype.Component;

@Component
class UserProvider implements IUserProvider {

    @Override
    public String getUserId() {
        var userName = System.getProperty("user.name");
        if (userName == null || userName.isBlank()) {
            throw new IllegalStateException("Windows user name is not available");
        }
        return userName;
    }
}

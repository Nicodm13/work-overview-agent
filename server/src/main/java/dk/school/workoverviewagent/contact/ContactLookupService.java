package dk.school.workoverviewagent.contact;

import dk.school.workoverviewagent.contact.api.IContactDirectory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContactLookupService {

    private static final int MAX_QUERY_LENGTH = 100;

    private final IContactDirectory contactDirectory;

    public ContactLookupService(IContactDirectory contactDirectory) {
        this.contactDirectory = contactDirectory;
    }

    public List<Contact> findContacts(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Contact search query must not be blank");
        }
        var normalizedQuery = query.strip();
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Contact search query must be 100 characters or fewer");
        }
        return contactDirectory.search(normalizedQuery);
    }
}

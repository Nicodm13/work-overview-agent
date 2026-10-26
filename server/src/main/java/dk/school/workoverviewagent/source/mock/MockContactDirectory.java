package dk.school.workoverviewagent.source.mock;

import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.api.IContactDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "mock")
class MockContactDirectory implements IContactDirectory {

    private static final int MAX_RESULTS = 10;

    private final MockSourceDataLoader dataLoader;

    MockContactDirectory(MockSourceDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    @Override
    public List<Contact> search(String query) {
        var normalizedQuery = query.toLowerCase(Locale.ROOT);
        var terms = Arrays.stream(normalizedQuery.split("\\s+"))
            .filter(term -> !term.isBlank())
            .toList();
        return dataLoader.loadContacts().stream()
            .filter(contact -> matches(contact, normalizedQuery, terms))
            .limit(MAX_RESULTS)
            .toList();
    }

    private static boolean matches(Contact contact, String query, List<String> terms) {
        var identity = Stream.of(contact.displayName(), contact.emailAddress(), contact.userPrincipalName())
            .filter(value -> value != null)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
        if (identity.stream().anyMatch(value -> value.contains(query))) {
            return true;
        }
        return terms.stream().anyMatch(term -> identity.stream()
            .flatMap(value -> Arrays.stream(value.split("[^\\p{L}\\p{N}@._-]+")))
            .anyMatch(word -> word.startsWith(term)));
    }
}

package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.api.IContactDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphContactDirectory implements IContactDirectory {

    private static final int MAX_RESULTS = 10;
    private static final List<String> SEARCHABLE_PROPERTIES = List.of(
        "displayName", "givenName", "surname", "mail", "userPrincipalName");

    private final GraphSourceClient graph;

    GraphContactDirectory(GraphSourceClient graph) {
        this.graph = graph;
    }

    @Override
    public List<Contact> search(String query) {
        var filter = graphFilter(query);
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("$select", "id,displayName,mail,userPrincipalName,jobTitle");
        parameters.put("$filter", filter);
        parameters.put("$top", Integer.toString(MAX_RESULTS));

        try {
            return graph.openForCurrentUser()
                .list(graph.uri("/users", parameters))
                .items()
                .stream()
                .map(GraphContactDirectory::toContact)
                .filter(contact -> contact.id() != null && contact.displayName() != null)
                .toList();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 403) {
                throw new IllegalStateException(
                    "Contact lookup requires the delegated Microsoft Graph User.ReadBasic.All permission and consent.",
                    exception);
            }
            throw exception;
        }
    }

    private static String graphFilter(String query) {
        var terms = new ArrayList<String>();
        if (query.contains("@")) {
            terms.add(query);
        } else {
            for (var term : query.strip().split("\\s+")) {
                if (!term.isBlank()) {
                    terms.add(term);
                }
            }
        }
        return terms.stream()
            .flatMap(term -> SEARCHABLE_PROPERTIES.stream()
                .map(property -> "startswith(" + property + ", '" + escapeODataLiteral(term) + "')"))
            .reduce((left, right) -> left + " or " + right)
            .orElseThrow(() -> new IllegalArgumentException("Contact search query must not be blank"));
    }

    private static String escapeODataLiteral(String value) {
        return value.replace("'", "''");
    }

    private static Contact toContact(Map<String, Object> user) {
        var mail = GraphValues.string(user, "mail");
        var userPrincipalName = GraphValues.string(user, "userPrincipalName");
        return new Contact(
            GraphValues.string(user, "id"),
            GraphValues.string(user, "displayName"),
            mail == null ? userPrincipalName : mail,
            userPrincipalName,
            GraphValues.string(user, "jobTitle"));
    }
}

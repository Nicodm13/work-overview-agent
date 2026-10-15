package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.graph.IGraphAccessTokenProvider;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GraphSourceClient {

    private static final int MAX_PAGES = 20;

    private final IGraphAccessTokenProvider tokenProvider;
    private final IUserProvider userProvider;
    private final RestClient restClient;
    private final URI root;

    GraphSourceClient(IGraphAccessTokenProvider tokenProvider, IUserProvider userProvider, RestClient restClient, URI root) {
        this.tokenProvider = tokenProvider;
        this.userProvider = userProvider;
        this.restClient = restClient;
        this.root = root;
    }

    Session open(SourceRequest request) {
        if (!request.ownerId().equals(userProvider.getUserId())) {
            throw new SecurityException("Source request owner does not match the authenticated user");
        }
        return new Session(tokenProvider.getAccessToken());
    }

    URI uri(String path, Map<String, String> query) {
        var builder = UriComponentsBuilder.fromUri(root).path(path);
        query.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    URI uriSegments(Map<String, String> query, String... segments) {
        var builder = UriComponentsBuilder.fromUri(root).pathSegment(segments);
        query.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private void requireGraphUri(URI uri) {
        if (!root.getScheme().equalsIgnoreCase(uri.getScheme())
            || !root.getAuthority().equalsIgnoreCase(uri.getAuthority())
            || !uri.getPath().startsWith(root.getPath() + "/")) {
            throw new IllegalArgumentException("Microsoft Graph paging URL changed origin or API version");
        }
    }

    final class Session {

        private final String token;

        private Session(String token) {
            this.token = token;
        }

        ListResult list(URI firstPage) {
            var items = new ArrayList<Map<String, Object>>();
            URI next = firstPage;
            var pageCount = 0;
            while (next != null && pageCount < MAX_PAGES) {
                var page = getJson(next);
                items.addAll(GraphValues.objects(page, "value"));
                var nextLink = GraphValues.string(page, "@odata.nextLink");
                next = nextLink == null ? null : URI.create(nextLink);
                pageCount++;
            }
            return new ListResult(List.copyOf(items), next != null);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> getJson(URI uri) {
            requireGraphUri(uri);
            var body = restClient.get().uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .header("Prefer", "outlook.timezone=\"UTC\"")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(Map.class);
            if (body == null) {
                throw new IllegalStateException("Microsoft Graph returned an empty JSON response");
            }
            return (Map<String, Object>) body;
        }

    }

    record ListResult(List<Map<String, Object>> items, boolean truncated) {
    }
}

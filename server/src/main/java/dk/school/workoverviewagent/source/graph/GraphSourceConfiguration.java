package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.graph.IGraphAccessTokenProvider;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphSourceConfiguration {

    @Bean
    GraphSourceClient graphSourceClient(IGraphAccessTokenProvider tokenProvider, IUserProvider userProvider) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return new GraphSourceClient(
            tokenProvider,
            userProvider,
            RestClient.builder().requestFactory(requestFactory).build(),
            URI.create("https://graph.microsoft.com/v1.0"));
    }
}

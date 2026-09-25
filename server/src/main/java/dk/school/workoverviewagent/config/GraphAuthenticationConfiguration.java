package dk.school.workoverviewagent.config;

import dk.school.workoverviewagent.graph.GraphAccessTokenProvider;
import dk.school.workoverviewagent.graph.IGraphConsentRequirements;
import dk.school.workoverviewagent.graph.IGraphAccessTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GraphAuthenticationProperties.class, McpSecurityProperties.class})
@ConditionalOnProperty(prefix = "work-overview.security", name = "enabled", havingValue = "true", matchIfMissing = true)
class GraphAuthenticationConfiguration {

    @Bean
    IGraphAccessTokenProvider graphAccessTokenProvider(
        GraphAuthenticationProperties graphProperties,
        McpSecurityProperties securityProperties,
        IGraphConsentRequirements consentRequirements) {
        graphProperties.validateCredentialConfiguration();
        return new GraphAccessTokenProvider(
            graphProperties,
            securityProperties.requiredTenantId(),
            consentRequirements);
    }
}

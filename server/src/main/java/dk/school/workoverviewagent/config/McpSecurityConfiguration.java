package dk.school.workoverviewagent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(McpSecurityProperties.class)
@ConditionalOnProperty(prefix = "work-overview.security", name = "enabled", havingValue = "true", matchIfMissing = true)
class McpSecurityConfiguration {

    @Bean
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error").permitAll()
                .requestMatchers("/mcp", "/mcp/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(McpSecurityProperties properties) {
        var issuerUri = properties.requiredIssuerUri();
        var decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        var audienceValidator = audienceValidator(properties.requiredAudience());
        var tenantValidator = tenantValidator(properties.requiredTenantId());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator, tenantValidator));
        return decoder;
    }

    private OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The token was not issued for this MCP server", null));
    }

    private OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> tenantValidator(String tenantId) {
        return jwt -> tenantId.equals(jwt.getClaimAsString("tid"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The token was not issued by the configured Entra tenant", null));
    }
}

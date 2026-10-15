package dk.school.workoverviewagent.source;

import dk.school.workoverviewagent.graph.IGraphAccessTokenProvider;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.user.IUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SourceModeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SourceModeTestConfiguration.class);

    @Test
    void missingProviderUsesOnlyGraphAdapters() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ISourceAdapter.class).values())
                .hasSize(4)
                .allMatch(adapter -> adapter.getClass().getPackageName().endsWith("source.graph"));
            assertThat(context).doesNotHaveBean("mockSourceDataLoader");
            assertThat(context).hasBean("graphSourceClient");
        });
    }

    @Test
    void explicitMockProviderUsesOnlyMockAdapters() {
        contextRunner.withPropertyValues("work-overview.sources.provider=mock").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ISourceAdapter.class).values())
                .hasSize(4)
                .allMatch(adapter -> adapter.getClass().getPackageName().endsWith("source.mock.adapter"));
            assertThat(context).hasBean("mockSourceDataLoader");
            assertThat(context).doesNotHaveBean("graphSourceClient");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("dk.school.workoverviewagent.source")
    static class SourceModeTestConfiguration {

        @Bean
        IGraphAccessTokenProvider graphAccessTokenProvider() {
            return () -> "test-token";
        }

        @Bean
        IUserProvider userProvider() {
            return () -> "tenant:user";
        }
    }
}

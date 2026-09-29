package dk.school.workoverviewagent.config;

import dk.school.workoverviewagent.Application;
import io.cucumber.spring.CucumberContextConfiguration;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@CucumberContextConfiguration
@SpringBootTest(classes = Application.class)
@Import(CucumberSpringConfiguration.TestUserConfiguration.class)
public class CucumberSpringConfiguration {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("work-overview.security.enabled", () -> false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestUserConfiguration {

        @Bean
        @Primary
        IUserProvider testUserProvider() {
            return () -> "test-tenant:test-user";
        }
    }
}

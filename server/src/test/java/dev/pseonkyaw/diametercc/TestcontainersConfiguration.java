package dev.pseonkyaw.diametercc;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers wiring shared by all integration tests that need a real
 * Postgres (matches the production image — postgres:17). Auto-bound to
 * Spring's {@code DataSource} via {@link ServiceConnection}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
            .withDatabaseName("diametercc")
            .withUsername("diametercc")
            .withPassword("diametercc");
    }
}

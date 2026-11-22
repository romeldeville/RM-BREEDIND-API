package com.rmbreeding.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests d'intégration.
 *
 * <p>Les conteneurs sont des singletons démarrés une seule fois pour toute la JVM : le
 * contexte Spring est mis en cache entre les classes de test, donc un conteneur arrêté en
 * fin de classe laisserait les classes suivantes pointer vers une base morte. Ils sont
 * supprimés à l'arrêt de la JVM par le reaper Testcontainers.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("rmbreeding")
                    .withUsername("rmbreeding")
                    .withPassword("rmbreeding");

    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                    .withEnv("MINIO_ROOT_USER", "rmbreeding")
                    .withEnv("MINIO_ROOT_PASSWORD", "rmbreeding-dev")
                    .withCommand("server", "/data")
                    .withExposedPorts(9000);

    @LocalServerPort
    protected int port;

    protected RestTestClient api() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        startContainers();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }

    private static synchronized void startContainers() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!MINIO.isRunning()) {
            MINIO.start();
        }
    }
}

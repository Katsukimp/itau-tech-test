package com.itau.banking.transaction;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Classe base para testes de integração com Testcontainers.
 * Utiliza containers singleton compartilhados entre todos os testes para melhor performance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private static final TestContainersManager containers = TestContainersManager.getInstance();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", containers.getPostgresContainer()::getJdbcUrl);
        registry.add("spring.datasource.username", containers.getPostgresContainer()::getUsername);
        registry.add("spring.datasource.password", containers.getPostgresContainer()::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Redis
        registry.add("spring.data.redis.host", containers.getRedisContainer()::getHost);
        registry.add("spring.data.redis.port", () -> containers.getRedisContainer().getMappedPort(6379));

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", containers.getKafkaContainer()::getBootstrapServers);
    }
}

package com.itau.banking.transaction;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton para gerenciar containers Testcontainers compartilhados entre todos os testes.
 * Garante que os containers sejam iniciados apenas uma vez e reusados por todas as classes de teste.
 */
public class TestContainersManager {

    private static final TestContainersManager INSTANCE = new TestContainersManager();

    private final PostgreSQLContainer<?> postgresContainer;
    private final GenericContainer<?> redisContainer;
    private final KafkaContainer kafkaContainer;

    private TestContainersManager() {
        // PostgreSQL
        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        postgresContainer.start();

        // Redis
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();

        // Kafka
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
        kafkaContainer.start();

        // Registrar shutdown hook para limpar containers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            kafkaContainer.stop();
            redisContainer.stop();
            postgresContainer.stop();
        }));
    }

    public static TestContainersManager getInstance() {
        return INSTANCE;
    }

    public PostgreSQLContainer<?> getPostgresContainer() {
        return postgresContainer;
    }

    public GenericContainer<?> getRedisContainer() {
        return redisContainer;
    }

    public KafkaContainer getKafkaContainer() {
        return kafkaContainer;
    }
}

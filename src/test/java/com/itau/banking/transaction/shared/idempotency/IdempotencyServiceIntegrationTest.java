package com.itau.banking.transaction.shared.idempotency;

import com.itau.banking.transaction.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@DisplayName("IdempotencyService - Integration Tests")
class IdempotencyServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // Limpar Redis antes de cada teste
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Deve validar e registrar chave de idempotência no Redis")
    void shouldValidateAndRegisterIdempotencyKey() {
        // Arrange
        String idempotencyKey = "test-key-" + System.currentTimeMillis();
        Long transactionId = 123L;

        // Act - Primeira validação deve passar
        boolean isValid = idempotencyService.isValidIdempotencyKey(idempotencyKey);
        assertThat(isValid).isTrue();

        // Registrar chave
        idempotencyService.registerIdempotencyKey(idempotencyKey, transactionId);

        // Segunda validação deve falhar (chave duplicada)
        boolean isValidAfter = idempotencyService.isValidIdempotencyKey(idempotencyKey);
        assertThat(isValidAfter).isFalse();
    }

    @Test
    @DisplayName("Deve recuperar transactionId por chave de idempotência")
    void shouldRetrieveTransactionIdByKey() {
        // Arrange
        String idempotencyKey = "retrieve-test-" + System.currentTimeMillis();
        Long transactionId = 456L;

        // Act
        idempotencyService.registerIdempotencyKey(idempotencyKey, transactionId);
        Long retrieved = idempotencyService.getTransactionByIdempotencyKey(idempotencyKey);

        // Assert
        assertThat(retrieved).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Deve permitir múltiplas chaves diferentes simultaneamente")
    void shouldAllowMultipleDifferentKeys() {
        // Arrange
        String key1 = "key1-" + System.currentTimeMillis();
        String key2 = "key2-" + System.currentTimeMillis();
        Long txId1 = 100L;
        Long txId2 = 200L;

        // Act
        idempotencyService.registerIdempotencyKey(key1, txId1);
        idempotencyService.registerIdempotencyKey(key2, txId2);

        // Assert
        assertThat(idempotencyService.getTransactionByIdempotencyKey(key1)).isEqualTo(txId1);
        assertThat(idempotencyService.getTransactionByIdempotencyKey(key2)).isEqualTo(txId2);
        assertThat(idempotencyService.isValidIdempotencyKey("key3-new")).isTrue();
    }

    @Test
    @DisplayName("Deve retornar null para chave não existente")
    void shouldReturnNull_ForNonExistentKey() {
        // Act
        Long result = idempotencyService.getTransactionByIdempotencyKey("non-existent-key");

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve validar corretamente chaves nulas ou vazias")
    void shouldValidateNullOrBlankKeys() {
        // Act & Assert
        assertThat(idempotencyService.isValidIdempotencyKey(null)).isFalse();
        assertThat(idempotencyService.isValidIdempotencyKey("")).isFalse();
        assertThat(idempotencyService.isValidIdempotencyKey("   ")).isFalse();
    }

    @Test
    @DisplayName("Deve persistir chave no Redis com TTL configurado")
    void shouldPersistKeyWithConfiguredTTL() {
        // Arrange
        String idempotencyKey = "ttl-test-" + System.currentTimeMillis();
        Long transactionId = 789L;

        // Act
        idempotencyService.registerIdempotencyKey(idempotencyKey, transactionId);

        // Assert - Verificar que chave existe no Redis
        String redisKey = "idempotency:" + idempotencyKey;
        Boolean exists = redisTemplate.hasKey(redisKey);
        assertThat(exists).isTrue();

        // Verificar TTL está configurado (deve ser 24 horas = 86400 segundos)
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(86000L); // Pelo menos 23h59min
        assertThat(ttl).isLessThanOrEqualTo(86400L); // No máximo 24h
    }
}

package com.itau.banking.transaction.shared.idempotency;

import com.itau.banking.transaction.shared.config.BankingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IdempotencyService - Unit Tests")
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private BankingProperties bankingProperties;

    @Mock
    private BankingProperties.Cache cacheProperties;

    @Mock
    private BankingProperties.Cache.Idempotency idempotencyCache;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(bankingProperties.getCache()).thenReturn(cacheProperties);
        when(cacheProperties.getIdempotency()).thenReturn(idempotencyCache);
        when(idempotencyCache.getPrefix()).thenReturn("idempotency:");
        when(idempotencyCache.getTtlHours()).thenReturn(24);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Deve retornar true quando chave é válida e não existe no Redis")
    void shouldReturnTrue_WhenKeyIsValidAndDoesNotExist() {
        // Arrange
        String idempotencyKey = "valid-key-123";
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Act
        boolean result = idempotencyService.isValidIdempotencyKey(idempotencyKey);

        // Assert
        assertThat(result).isTrue();
        verify(redisTemplate, times(1)).hasKey("idempotency:" + idempotencyKey);
    }

    @Test
    @DisplayName("Deve retornar false quando chave já existe no Redis")
    void shouldReturnFalse_WhenKeyAlreadyExists() {
        // Arrange
        String idempotencyKey = "existing-key-456";
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        // Act
        boolean result = idempotencyService.isValidIdempotencyKey(idempotencyKey);

        // Assert
        assertThat(result).isFalse();
        verify(redisTemplate, times(1)).hasKey("idempotency:" + idempotencyKey);
    }

    @Test
    @DisplayName("Deve retornar false quando chave é nula")
    void shouldReturnFalse_WhenKeyIsNull() {
        // Act
        boolean result = idempotencyService.isValidIdempotencyKey(null);

        // Assert
        assertThat(result).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("Deve retornar false quando chave é vazia")
    void shouldReturnFalse_WhenKeyIsBlank() {
        // Act
        boolean result1 = idempotencyService.isValidIdempotencyKey("");
        boolean result2 = idempotencyService.isValidIdempotencyKey("   ");

        // Assert
        assertThat(result1).isFalse();
        assertThat(result2).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("Deve registrar chave de idempotência com TTL correto")
    void shouldRegisterIdempotencyKeyWithCorrectTTL() {
        // Arrange
        String idempotencyKey = "new-key-789";
        Long transactionId = 100L;

        // Act
        idempotencyService.registerIdempotencyKey(idempotencyKey, transactionId);

        // Assert
        verify(valueOperations, times(1)).set(
                eq("idempotency:" + idempotencyKey),
                eq("100"),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("Deve registrar múltiplas chaves diferentes")
    void shouldRegisterMultipleDifferentKeys() {
        // Arrange
        String key1 = "key-001";
        String key2 = "key-002";
        Long txId1 = 1L;
        Long txId2 = 2L;

        // Act
        idempotencyService.registerIdempotencyKey(key1, txId1);
        idempotencyService.registerIdempotencyKey(key2, txId2);

        // Assert
        verify(valueOperations, times(1)).set(
                eq("idempotency:" + key1),
                eq("1"),
                any(Duration.class)
        );
        verify(valueOperations, times(1)).set(
                eq("idempotency:" + key2),
                eq("2"),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("Deve recuperar transactionId por chave de idempotência")
    void shouldGetTransactionIdByIdempotencyKey() {
        // Arrange
        String idempotencyKey = "existing-key";
        String expectedTransactionId = "999";
        when(valueOperations.get("idempotency:" + idempotencyKey))
                .thenReturn(expectedTransactionId);

        // Act
        Long result = idempotencyService.getTransactionByIdempotencyKey(idempotencyKey);

        // Assert
        assertThat(result).isEqualTo(999L);
        verify(valueOperations, times(1)).get("idempotency:" + idempotencyKey);
    }

    @Test
    @DisplayName("Deve retornar null quando chave não existe no Redis")
    void shouldReturnNull_WhenKeyDoesNotExist() {
        // Arrange
        String idempotencyKey = "non-existing-key";
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        Long result = idempotencyService.getTransactionByIdempotencyKey(idempotencyKey);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve usar prefixo correto configurado")
    void shouldUseCorrectConfiguredPrefix() {
        // Arrange
        when(idempotencyCache.getPrefix()).thenReturn("custom-prefix:");
        String idempotencyKey = "test-key";
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Act
        idempotencyService.isValidIdempotencyKey(idempotencyKey);

        // Assert
        verify(redisTemplate, times(1)).hasKey("custom-prefix:test-key");
    }

    @Test
    @DisplayName("Deve usar TTL correto configurado")
    void shouldUseCorrectConfiguredTTL() {
        // Arrange
        when(idempotencyCache.getTtlHours()).thenReturn(48);
        String idempotencyKey = "test-key";
        Long transactionId = 123L;

        // Act
        idempotencyService.registerIdempotencyKey(idempotencyKey, transactionId);

        // Assert
        verify(valueOperations, times(1)).set(
                anyString(),
                anyString(),
                eq(Duration.ofHours(48))
        );
    }
}

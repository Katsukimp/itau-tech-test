package com.itau.banking.transaction.limit;

import com.itau.banking.transaction.shared.config.BankingProperties;
import com.itau.banking.transaction.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLimitService - Unit Tests")
class DailyLimitServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DailyLimitControlRepository dailyLimitRepository;

    @Mock
    private RedisTemplate<String, BigDecimal> redisTemplate;

    @Mock
    private ValueOperations<String, BigDecimal> valueOperations;

    @Mock
    private BankingProperties bankingProperties;

    @Mock
    private BankingProperties.Cache cacheProperties;

    @Mock
    private BankingProperties.Cache.DailyLimit dailyLimitCache;

    @InjectMocks
    private DailyLimitService dailyLimitService;

    @BeforeEach
    void setUp() {
        when(bankingProperties.getCache()).thenReturn(cacheProperties);
        when(cacheProperties.getDailyLimit()).thenReturn(dailyLimitCache);
        when(dailyLimitCache.getPrefix()).thenReturn("daily-limit:");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Deve permitir transferência quando dentro do limite")
    void shouldAllowTransfer_WhenWithinLimit() {
        // Arrange
        Long accountId = 1L;
        BigDecimal dailyLimit = new BigDecimal("1000.00");
        BigDecimal currentTotal = new BigDecimal("300.00");
        BigDecimal transferAmount = new BigDecimal("500.00");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        boolean result = dailyLimitService.canTransfer(accountId, dailyLimit, transferAmount);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Deve bloquear transferência quando excede limite")
    void shouldBlockTransfer_WhenExceedsLimit() {
        // Arrange
        Long accountId = 1L;
        BigDecimal dailyLimit = new BigDecimal("1000.00");
        BigDecimal currentTotal = new BigDecimal("800.00");
        BigDecimal transferAmount = new BigDecimal("300.00");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        boolean result = dailyLimitService.canTransfer(accountId, dailyLimit, transferAmount);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Deve permitir quando soma exata igual ao limite")
    void shouldAllow_WhenSumEqualsLimit() {
        // Arrange
        Long accountId = 1L;
        BigDecimal dailyLimit = new BigDecimal("1000.00");
        BigDecimal currentTotal = new BigDecimal("600.00");
        BigDecimal transferAmount = new BigDecimal("400.00");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        boolean result = dailyLimitService.canTransfer(accountId, dailyLimit, transferAmount);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Deve buscar total do Redis quando disponível")
    void shouldGetTotalFromRedis_WhenAvailable() {
        // Arrange
        Long accountId = 1L;
        BigDecimal cachedValue = new BigDecimal("250.00");
        when(valueOperations.get(anyString())).thenReturn(cachedValue);

        // Act
        BigDecimal result = dailyLimitService.getCurrentDailyTotal(accountId);

        // Assert
        assertThat(result).isEqualByComparingTo(cachedValue);
        verify(valueOperations, times(1)).get(anyString());
        verify(dailyLimitRepository, never()).findByAccountIdAndDate(any(), any());
        verify(transactionRepository, never()).sumDailyTransactionsByAccountId(any(), any(), any());
    }

    @Test
    @DisplayName("Deve buscar do banco quando Redis falha")
    void shouldGetFromDatabase_WhenRedisFails() {
        // Arrange
        Long accountId = 1L;
        LocalDate today = LocalDate.now();
        BigDecimal dbValue = new BigDecimal("400.00");

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        DailyLimitControl control = new DailyLimitControl();
        control.setTotalAmount(dbValue);
        when(dailyLimitRepository.findByAccountIdAndDate(accountId, today))
                .thenReturn(Optional.of(control));

        // Act
        BigDecimal result = dailyLimitService.getCurrentDailyTotal(accountId);

        // Assert
        assertThat(result).isEqualByComparingTo(dbValue);
        verify(dailyLimitRepository, times(1)).findByAccountIdAndDate(accountId, today);
    }

    @Test
    @DisplayName("Deve calcular de transações quando não existe cache")
    void shouldCalculateFromTransactions_WhenNoCacheExists() {
        // Arrange
        Long accountId = 1L;
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        BigDecimal calculatedTotal = new BigDecimal("150.00");

        when(valueOperations.get(anyString())).thenReturn(null);
        when(dailyLimitRepository.findByAccountIdAndDate(accountId, today))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumDailyTransactionsByAccountId(accountId, startOfDay, endOfDay))
                .thenReturn(calculatedTotal);

        // Act
        BigDecimal result = dailyLimitService.getCurrentDailyTotal(accountId);

        // Assert
        assertThat(result).isEqualByComparingTo(calculatedTotal);
        verify(transactionRepository, times(1))
                .sumDailyTransactionsByAccountId(accountId, startOfDay, endOfDay);
        verify(dailyLimitRepository, times(1)).save(any(DailyLimitControl.class));
    }

    @Test
    @DisplayName("Deve atualizar após transferência")
    void shouldUpdateAfterTransfer() {
        // Arrange
        Long accountId = 1L;
        BigDecimal currentTotal = new BigDecimal("500.00");
        BigDecimal transferAmount = new BigDecimal("200.00");
        BigDecimal expectedNewTotal = new BigDecimal("700.00");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        dailyLimitService.updateAfterTransfer(accountId, transferAmount);

        // Assert
        verify(valueOperations, times(1)).set(anyString(), eq(expectedNewTotal), any());
        verify(dailyLimitRepository, times(1)).save(any(DailyLimitControl.class));
    }

    @Test
    @DisplayName("Deve retornar zero quando não há transações")
    void shouldReturnZero_WhenNoTransactions() {
        // Arrange
        Long accountId = 1L;
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(dailyLimitRepository.findByAccountIdAndDate(accountId, today))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumDailyTransactionsByAccountId(accountId, startOfDay, endOfDay))
                .thenReturn(BigDecimal.ZERO);

        // Act
        BigDecimal result = dailyLimitService.getCurrentDailyTotal(accountId);

        // Assert
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Deve usar diferentes limites para diferentes contas")
    void shouldUseDifferentLimitsForDifferentAccounts() {
        // Arrange
        Long account1 = 1L;
        Long account2 = 2L;
        BigDecimal limit1 = new BigDecimal("1000.00");
        BigDecimal limit2 = new BigDecimal("5000.00");
        BigDecimal amount = new BigDecimal("1500.00");
        BigDecimal currentTotal = new BigDecimal("0.00");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        boolean result1 = dailyLimitService.canTransfer(account1, limit1, amount);
        boolean result2 = dailyLimitService.canTransfer(account2, limit2, amount);

        // Assert
        assertThat(result1).isFalse(); // Excede limite de 1000
        assertThat(result2).isTrue();  // Dentro do limite de 5000
    }

    @Test
    @DisplayName("Deve lidar com valores decimais precisos")
    void shouldHandlePreciseDecimalValues() {
        // Arrange
        Long accountId = 1L;
        BigDecimal dailyLimit = new BigDecimal("1000.00");
        BigDecimal currentTotal = new BigDecimal("999.99");
        BigDecimal transferAmount = new BigDecimal("0.01");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        boolean result = dailyLimitService.canTransfer(accountId, dailyLimit, transferAmount);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Deve bloquear quando excede por centavos")
    void shouldBlock_WhenExceedsByPennies() {
        // Arrange
        Long accountId = 1L;
        BigDecimal dailyLimit = new BigDecimal("1000.00");
        BigDecimal currentTotal = new BigDecimal("999.99");
        BigDecimal transferAmount = new BigDecimal("0.02");

        when(valueOperations.get(anyString())).thenReturn(currentTotal);

        // Act
        boolean result = dailyLimitService.canTransfer(accountId, dailyLimit, transferAmount);

        // Assert
        assertThat(result).isFalse();
    }
}

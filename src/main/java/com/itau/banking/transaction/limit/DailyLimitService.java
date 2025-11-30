package com.itau.banking.transaction.limit;

import com.itau.banking.transaction.shared.config.BankingProperties;
import com.itau.banking.transaction.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyLimitService {

    private final TransactionRepository transactionRepository;
    private final DailyLimitControlRepository dailyLimitRepository;
    private final RedisTemplate<String, BigDecimal> redisTemplate;
    private final BankingProperties bankingProperties;

    public boolean canTransfer(Long accountId, BigDecimal accountDailyLimit, BigDecimal transferAmount) {
        BigDecimal currentTotal = getCurrentDailyTotal(accountId);
        BigDecimal newTotal = currentTotal.add(transferAmount);

        // Usa o limite específico da conta
        return newTotal.compareTo(accountDailyLimit) <= 0;
    }

    /**
     * Obtém o total já transferido hoje usando 3 camadas de cache
     */
    public BigDecimal getCurrentDailyTotal(Long accountId) {
        LocalDate today = LocalDate.now();

        // 1. Tenta Redis (mais rápido)
        BigDecimal cached = getFromRedis(accountId, today);
        if (cached != null) {
            log.debug("Daily limit found in Redis for account {}", accountId);
            return cached;
        }

        // 2. Tenta daily_limit_control (rápido)
        cached = getFromDatabase(accountId, today);
        if (cached != null) {
            log.debug("Daily limit found in database for account {}", accountId);
            // Popula Redis
            saveToRedis(accountId, today, cached);
            return cached;
        }

        // 3. Calcula do source of truth (lento mas correto)
        cached = calculateFromTransactions(accountId, today);
        log.debug("Daily limit calculated from transactions for account {}", accountId);

        // Popula ambos os caches
        saveToDatabase(accountId, today, cached);
        saveToRedis(accountId, today, cached);

        return cached;
    }

    /**
     * Atualiza o limite após transferência bem-sucedida
     */
    public void updateAfterTransfer(Long accountId, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        BigDecimal newTotal = getCurrentDailyTotal(accountId).add(amount);

        // Atualiza ambos os caches (assíncrono seria ideal)
        saveToDatabase(accountId, today, newTotal);
        saveToRedis(accountId, today, newTotal);
    }

    // ==================== Camadas de Cache ====================

    private BigDecimal getFromRedis(Long accountId, LocalDate date) {
        try {
            String key = buildRedisKey(accountId, date);
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis error, falling back to database: {}", e.getMessage());
            return null;  // Graceful degradation
        }
    }

    private void saveToRedis(Long accountId, LocalDate date, BigDecimal amount) {
        try {
            String key = buildRedisKey(accountId, date);
            LocalDateTime midnight = date.plusDays(1).atStartOfDay();
            Duration ttl = Duration.between(LocalDateTime.now(), midnight);

            redisTemplate.opsForValue().set(key, amount, ttl);
        } catch (Exception e) {
            log.warn("Failed to save to Redis: {}", e.getMessage());
            // Não falha a operação se Redis cair
        }
    }

    private BigDecimal getFromDatabase(Long accountId, LocalDate date) {
        return dailyLimitRepository
                .findByAccountIdAndDate(accountId, date)
                .map(DailyLimitControl::getTotalAmount)
                .orElse(null);
    }

    private void saveToDatabase(Long accountId, LocalDate date, BigDecimal amount) {
        DailyLimitControl control = dailyLimitRepository
                .findByAccountIdAndDate(accountId, date)
                .orElse(new DailyLimitControl());

        control.setAccountId(accountId);
        control.setDate(date);
        control.setTotalAmount(amount);
        control.setTransactionCount(control.getTransactionCount() + 1);
        control.setLastUpdatedAt(LocalDateTime.now());

        dailyLimitRepository.save(control);
    }

    private BigDecimal calculateFromTransactions(Long accountId, LocalDate date) {
        return transactionRepository.sumDailyTransactionsByAccountId(accountId, date);
    }

    private String buildRedisKey(Long accountId, LocalDate date) {
        return String.format("%s%d:%s", bankingProperties.getCache().getDailyLimit().getPrefix(), accountId, date);
    }
}

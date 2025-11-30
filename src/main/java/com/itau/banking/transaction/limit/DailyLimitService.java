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
        return newTotal.compareTo(accountDailyLimit) <= 0;
    }

    public BigDecimal getCurrentDailyTotal(Long accountId) {
        LocalDate today = LocalDate.now();

        BigDecimal cached = getFromRedis(accountId, today);
        if (cached != null) {
            log.debug("[DailyLimitService].[getCurrentDailyTotal] - Limite diário encontrado no Redis - Conta: {}", accountId);
            return cached;
        }

        cached = getFromDatabase(accountId, today);
        if (cached != null) {
            log.debug("[DailyLimitService].[getCurrentDailyTotal] - Limite diário encontrado no banco - Conta: {}", accountId);
            saveToRedis(accountId, today, cached);
            return cached;
        }

        cached = calculateFromTransactions(accountId, today);
        log.debug("[DailyLimitService].[getCurrentDailyTotal] - Limite diário calculado das transações - Conta: {}", accountId);

        saveToDatabase(accountId, today, cached);
        saveToRedis(accountId, today, cached);

        return cached;
    }

    public void updateAfterTransfer(Long accountId, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        BigDecimal newTotal = getCurrentDailyTotal(accountId).add(amount);

        saveToDatabase(accountId, today, newTotal);
        saveToRedis(accountId, today, newTotal);
    }

    private BigDecimal getFromRedis(Long accountId, LocalDate date) {
        try {
            String key = buildRedisKey(accountId, date);
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[DailyLimitService].[getFromRedis] - Erro no Redis, usando fallback para banco: {}", e.getMessage());
            return null;
        }
    }

    private void saveToRedis(Long accountId, LocalDate date, BigDecimal amount) {
        try {
            String key = buildRedisKey(accountId, date);
            LocalDateTime midnight = date.plusDays(1).atStartOfDay();
            Duration ttl = Duration.between(LocalDateTime.now(), midnight);

            redisTemplate.opsForValue().set(key, amount, ttl);
        } catch (Exception e) {
            log.warn("[DailyLimitService].[saveToRedis] - Falha ao salvar no Redis: {}", e.getMessage());
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

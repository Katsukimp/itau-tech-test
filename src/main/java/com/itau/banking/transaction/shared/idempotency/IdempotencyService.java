package com.itau.banking.transaction.shared.idempotency;

import com.itau.banking.transaction.shared.config.BankingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final BankingProperties bankingProperties;
    
    public boolean isValidIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("[IdempotencyService].[isValidIdempotencyKey] - Chave de idempotência nula ou vazia");
            return false;
        }
        
        String key = bankingProperties.getCache().getIdempotency().getPrefix() + idempotencyKey;
        Boolean exists = redisTemplate.hasKey(key);
        
        if (Boolean.TRUE.equals(exists)) {
            log.warn("[IdempotencyService].[isValidIdempotencyKey] - Chave de idempotência duplicada detectada: {}", idempotencyKey);
            return false;
        }
        
        return true;
    }
    
    public void registerIdempotencyKey(String idempotencyKey, Long transactionId) {
        String key = bankingProperties.getCache().getIdempotency().getPrefix() + idempotencyKey;
        Duration ttl = Duration.ofHours(bankingProperties.getCache().getIdempotency().getTtlHours());
        redisTemplate.opsForValue().set(key, transactionId.toString(), ttl);
        log.info("[IdempotencyService].[registerIdempotencyKey] - Chave de idempotência registrada: {} -> Transaction: {}", idempotencyKey, transactionId);
    }
    
    public Long getTransactionByIdempotencyKey(String idempotencyKey) {
        String key = bankingProperties.getCache().getIdempotency().getPrefix() + idempotencyKey;
        String transactionId = redisTemplate.opsForValue().get(key);
        
        if (transactionId != null) {
            return Long.parseLong(transactionId);
        }
        
        return null;
    }
}

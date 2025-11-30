package com.itau.banking.transaction.integration.bacen;

import com.itau.banking.transaction.shared.config.BacenMockProperties;
import com.itau.banking.transaction.shared.exception.BacenApiException;
import com.itau.banking.transaction.integration.bacen.dto.BacenNotificationRequest;
import com.itau.banking.transaction.integration.bacen.dto.BacenNotificationResponse;
import com.itau.banking.transaction.shared.exception.BacenRateLimitException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class BacenApiClient {
    
    private final BacenMockProperties bacenMockProperties;

    @CircuitBreaker(name = "bacenApi", fallbackMethod = "notifyTransactionFallback")
    @Retry(name = "bacenApi")
    public BacenNotificationResponse notifyTransaction(BacenNotificationRequest request) {
        log.info("Mock BACEN: Notificando transação {} - Valor: {} - Origem: {} -> Destino: {}",
                request.getTransactionId(),
                request.getAmount(),
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber());

        if (Math.random() < bacenMockProperties.getRateLimitRate()) {
            log.warn("Mock BACEN: Rate limit excedido (429) na transação {}", request.getTransactionId());
            throw new BacenRateLimitException("HTTP 429 - Too Many Requests: Rate limit excedido no BACEN");
        }
        
        if (Math.random() < bacenMockProperties.getTimeoutRate()) {
            log.error("Mock BACEN: Timeout na transação {}", request.getTransactionId());
            throw new BacenApiException("Timeout ao comunicar com BACEN - Transação: " + request.getTransactionId());
        }
        
        if (Math.random() < bacenMockProperties.getFailureRate()) {
            log.error("Mock BACEN: Erro de comunicação na transação {}", request.getTransactionId());
            throw new BacenApiException("Erro de comunicação com BACEN - Transação: " + request.getTransactionId());
        }
        
        String protocol = generateProtocol();
        BacenNotificationResponse response = BacenNotificationResponse.builder()
                .protocol(protocol)
                .status("ACCEPTED")
                .timestamp(LocalDateTime.now())
                .message("Notificação processada com sucesso")
                .build();
        
        log.info("Mock BACEN: Transação {} notificada com sucesso - Protocolo: {}", 
                request.getTransactionId(), protocol);
        
        return response;
    }

    private BacenNotificationResponse notifyTransactionFallback(
            BacenNotificationRequest request,
            Exception exception
    ) {
        log.error("[BacenApiClient].[fallback] - Circuit aberto ou retry esgotado: {}", exception.getMessage());
        throw new BacenApiException("BACEN indisponível no momento");
    }
    
    private String generateProtocol() {
        return "BACEN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

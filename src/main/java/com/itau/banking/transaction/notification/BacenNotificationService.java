package com.itau.banking.transaction.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itau.banking.transaction.integration.bacen.BacenApiClient;
import com.itau.banking.transaction.integration.bacen.dto.BacenNotificationRequest;
import com.itau.banking.transaction.integration.bacen.dto.BacenNotificationResponse;
import com.itau.banking.transaction.integration.customer.dto.CustomerDto;
import com.itau.banking.transaction.notification.dto.BacenKafkaMessage;
import com.itau.banking.transaction.notification.kafka.BacenNotificationProducer;
import com.itau.banking.transaction.shared.config.BankingProperties;
import com.itau.banking.transaction.shared.enums.NotificationStatus;
import com.itau.banking.transaction.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacenNotificationService {

    private final BacenNotificationRepository bacenNotificationRepository;
    private final BacenApiClient bacenApiClient;
    private final ObjectMapper objectMapper;
    private final BacenNotificationProducer kafkaProducer;
    private final BankingProperties bankingProperties;

    @Transactional
    public void saveOutbox(Transaction transaction, CustomerDto customer) {
        log.info("[BacenNotificationService].[saveOutboxOnly] - Salvando notificação na Outbox - Transaction: {}", 
                transaction.getId());

        try {
            BacenNotificationRequest request = BacenNotificationRequest.builder()
                    .transactionId(transaction.getId())
                    .idempotencyKey(transaction.getIdempotencyKey())
                    .sourceAccountNumber(transaction.getSourceAccount().getAccountNumber())
                    .destinationAccountNumber(transaction.getDestinationAccount().getAccountNumber())
                    .amount(transaction.getAmount())
                    .customerName(customer.getName())
                    .customerCpf(customer.getCpf())
                    .transactionDate(transaction.getCreatedAt())
                    .build();

            String payload = objectMapper.writeValueAsString(request);

            BacenNotification notification = new BacenNotification();
            notification.setTransaction(transaction);
            notification.setIdempotencyKey(transaction.getIdempotencyKey());
            notification.setStatus(NotificationStatus.PENDING);
            notification.setPayload(payload);
            notification.setRetryCount(0);
            bacenNotificationRepository.save(notification);

            log.info("[BacenNotificationService].[saveOutboxOnly] - Notificação salva na Outbox com sucesso - Notification: {}", 
                    notification.getId());

        } catch (Exception e) {
            log.error("[BacenNotificationService].[saveOutboxOnly] - Erro ao salvar notificação na Outbox: {}", 
                    e.getMessage(), e);
            throw new RuntimeException("Falha ao salvar notificação BACEN na Outbox", e);
        }
    }

    public void sendSync(Transaction transaction, CustomerDto customer) {
        log.info("[BacenNotificationService].[sendSync] - Tentando envio síncrono ao BACEN - Transaction: {}",
                transaction.getId());

        try {
            BacenNotificationRequest request = BacenNotificationRequest.builder()
                    .transactionId(transaction.getId())
                    .idempotencyKey(transaction.getIdempotencyKey())
                    .sourceAccountNumber(transaction.getSourceAccount().getAccountNumber())
                    .destinationAccountNumber(transaction.getDestinationAccount().getAccountNumber())
                    .amount(transaction.getAmount())
                    .customerName(customer.getName())
                    .customerCpf(customer.getCpf())
                    .transactionDate(transaction.getCreatedAt())
                    .build();

            BacenNotificationResponse response = bacenApiClient.notifyTransaction(request);
            
            updateNotificationToSent(transaction.getIdempotencyKey(), response.getProtocol());
            
            log.info("[BacenNotificationService].[sendSync] - Notificação BACEN enviada com sucesso (síncrono) - Protocol: {}",
                    response.getProtocol());
            
        } catch (Exception syncException) {
            log.warn("[BacenNotificationService].[sendSync] - Falha no envio síncrono, enviando para Kafka - Error: {}",
                    syncException.getMessage());
            
            BacenNotification notification = bacenNotificationRepository
                    .findByIdempotencyKeyAndStatus(transaction.getIdempotencyKey(), NotificationStatus.PENDING)
                    .orElseThrow(() -> new RuntimeException("Notification not found in Outbox"));
            
            BacenKafkaMessage kafkaMessage = BacenKafkaMessage.builder()
                    .transactionId(transaction.getId())
                    .notificationId(notification.getId())
                    .idempotencyKey(transaction.getIdempotencyKey())
                    .sourceAccountId(transaction.getSourceAccount().getId())
                    .sourceAccountNumber(transaction.getSourceAccount().getAccountNumber())
                    .destinationAccountId(transaction.getDestinationAccount().getId())
                    .destinationAccountNumber(transaction.getDestinationAccount().getAccountNumber())
                    .amount(transaction.getAmount())
                    .customerName(customer.getName())
                    .customerCpf(customer.getCpf())
                    .transactionDate(transaction.getCreatedAt())
                    .retryCount(0)
                    .build();
            
            kafkaProducer.sendNotification(kafkaMessage);
            
            log.info("[BacenNotificationService].[sendSyncWithRetry] - Notificação enviada para Kafka (fallback assíncrono) - Notification: {}", 
                    notification.getId());
        }
    }

    @Transactional
    public void updateNotificationToSent(String idempotencyKey, String protocol) {
        BacenNotification notification = bacenNotificationRepository
                .findByIdempotencyKeyAndStatus(idempotencyKey, NotificationStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("Notification not found in Outbox"));
        
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notification.setProtocol(protocol);
        bacenNotificationRepository.save(notification);
    }

    @Transactional
    public void processNotification(BacenNotification notification) {
        log.info("[BacenNotificationService].[processNotification] - Processando notificação BACEN - Notification: {} - Retry: {}", 
                notification.getId(), notification.getRetryCount());

        try {
            BacenNotificationRequest request = objectMapper.readValue(
                    notification.getPayload(), 
                    BacenNotificationRequest.class
            );

            BacenNotificationResponse response = bacenApiClient.notifyTransaction(request);

            notification.setStatus(NotificationStatus.SENT);
            notification.setLastAttemptAt(LocalDateTime.now());
            notification.setProtocol(response.getProtocol());

            bacenNotificationRepository.save(notification);

            log.info("[BacenNotificationService].[processNotification] - Notificação BACEN enviada com sucesso - Protocol: {}", 
                    response.getProtocol());

        } catch (Exception e) {
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setLastAttemptAt(LocalDateTime.now());
            notification.setErrorMessage(e.getMessage());

            if (notification.getRetryCount() >= bankingProperties.getNotification().getMaxFailedAttempts()) {
                notification.setStatus(NotificationStatus.FAILED);
                log.error("[BacenNotificationService].[processNotification] - Notificação BACEN falhou após 10 tentativas - Notification: {} - Será reprocessada pelo scheduler de FAILED", 
                        notification.getId());
            } else {
                notification.setStatus(NotificationStatus.PENDING);
                log.warn("[BacenNotificationService].[processNotification] - Falha ao enviar notificação BACEN, será retentado - Notification: {} - Retry: {}", 
                        notification.getId(), notification.getRetryCount());
            }

            bacenNotificationRepository.save(notification);
        }
    }

    public void processAllPendingNotifications() {
        log.info("[BacenNotificationService].[processAllPendingNotifications] - Buscando notificações pendentes");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(bankingProperties.getScheduler().getPendingMinAgeMinutes());
        var pendingNotifications = bacenNotificationRepository
                .findByStatusAndCreatedAtBefore(NotificationStatus.PENDING, cutoffTime);

        log.info("[BacenNotificationService].[processAllPendingNotifications] - Encontradas {} notificações pendentes", 
                pendingNotifications.size());

        pendingNotifications.forEach(this::processNotification);
    }

    public void processAllFailedNotifications() {
        log.info("[BacenNotificationService].[processAllFailedNotifications] - Buscando notificações FAILED para reprocessamento");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(bankingProperties.getNotification().getFailedRetryDelayMinutes());
        var failedNotifications = bacenNotificationRepository
                .findByStatusAndLastAttemptAtBefore(NotificationStatus.FAILED, cutoffTime);

        log.info("[BacenNotificationService].[processAllFailedNotifications] - Encontradas {} notificações FAILED para reprocessar", 
                failedNotifications.size());

        failedNotifications.forEach(notification -> {
            notification.setStatus(NotificationStatus.PENDING);
            notification.setRetryCount(0);
            bacenNotificationRepository.save(notification);
            
            log.info("[BacenNotificationService].[processAllFailedNotifications] - Notificação {} movida de FAILED para PENDING", 
                    notification.getId());
            
            processNotification(notification);
        });
    }
}

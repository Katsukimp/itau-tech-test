package com.itau.banking.transaction.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itau.banking.transaction.integration.bacen.BacenApiClient;
import com.itau.banking.transaction.integration.bacen.dto.BacenNotificationRequest;
import com.itau.banking.transaction.integration.bacen.dto.BacenNotificationResponse;
import com.itau.banking.transaction.notification.BacenNotification;
import com.itau.banking.transaction.notification.BacenNotificationRepository;
import com.itau.banking.transaction.notification.dto.BacenKafkaMessage;
import com.itau.banking.transaction.shared.config.BankingProperties;
import com.itau.banking.transaction.shared.config.KafkaTopicConfig;
import com.itau.banking.transaction.shared.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BacenNotificationConsumer {
    
    private final BacenApiClient bacenApiClient;
    private final BacenNotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final BankingProperties bankingProperties;
    
    @KafkaListener(topics = KafkaTopicConfig.BACEN_NOTIFICATIONS_TOPIC, groupId = "banking-transaction-api-group")
    public void consumeNotification(String message, Acknowledgment acknowledgment) {
        try {
            log.info("[Kafka Consumer] Received message: {}", message);
            
            BacenKafkaMessage kafkaMessage = objectMapper.readValue(message, BacenKafkaMessage.class);
            
            processNotification(kafkaMessage);
            
            acknowledgment.acknowledge();
            log.info("[Kafka Consumer] Message acknowledged successfully - Transaction: {}", kafkaMessage.getTransactionId());
            
        } catch (Exception e) {
            log.error("[Kafka Consumer] Error processing message: {} - Error: {} - Message will be reprocessed", 
                    message, e.getMessage());
        }
    }
    
    @Transactional
    private void processNotification(BacenKafkaMessage kafkaMessage) {
        try {
            BacenNotification notification = notificationRepository.findById(kafkaMessage.getNotificationId())
                    .orElseThrow(() -> new RuntimeException("Notification not found: " + kafkaMessage.getNotificationId()));
            
            if (notification.getStatus() == NotificationStatus.SENT) {
                log.info("[Kafka Consumer] Notification already processed (idempotent) - NotificationId: {} - IdempotencyKey: {} - Protocol: {}", 
                        notification.getId(), 
                        notification.getIdempotencyKey(),
                        notification.getProtocol());
                return;
            }
            
            if (kafkaMessage.getIdempotencyKey() != null && !kafkaMessage.getIdempotencyKey().equals(notification.getIdempotencyKey())) {
                log.warn("[Kafka Consumer] Idempotency key mismatch - Kafka: {} vs DB: {} - Skipping",
                        kafkaMessage.getIdempotencyKey(),
                        notification.getIdempotencyKey());
                return;
            }
            
            BacenNotificationRequest request = BacenNotificationRequest.builder()
                    .transactionId(kafkaMessage.getTransactionId())
                    .idempotencyKey(kafkaMessage.getIdempotencyKey())
                    .sourceAccountNumber(kafkaMessage.getSourceAccountNumber())
                    .destinationAccountNumber(kafkaMessage.getDestinationAccountNumber())
                    .amount(kafkaMessage.getAmount())
                    .customerName(kafkaMessage.getCustomerName())
                    .customerCpf(kafkaMessage.getCustomerCpf())
                    .transactionDate(kafkaMessage.getTransactionDate())
                    .build();
            
            BacenNotificationResponse response = bacenApiClient.notifyTransaction(request);
            
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setProtocol(response.getProtocol());
            notificationRepository.save(notification);
            
            log.info("[Kafka Consumer] Notification sent successfully via Kafka fallback - Transaction: {} - Protocol: {}",
                    kafkaMessage.getTransactionId(),
                    response.getProtocol());
            
        } catch (Exception e) {
            log.error("[Kafka Consumer] Error sending notification to BACEN - Transaction: {} - Error: {}",
                    kafkaMessage.getTransactionId(),
                    e.getMessage());
            
            BacenNotification notification = notificationRepository.findById(kafkaMessage.getNotificationId())
                    .orElseThrow(() -> new RuntimeException("Notification not found: " + kafkaMessage.getNotificationId()));
            
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setLastAttemptAt(LocalDateTime.now());
            notification.setErrorMessage(e.getMessage());
            
            if (notification.getRetryCount() >= bankingProperties.getNotification().getMaxRetryAttempts()) {
                notification.setStatus(NotificationStatus.FAILED);
                log.error("[Kafka Consumer] Max retry attempts reached for notification: {} - Marking as FAILED",
                        notification.getId());
            }
            
            notificationRepository.save(notification);
        }
    }
}

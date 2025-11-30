package com.itau.banking.transaction.notification;

import com.itau.banking.transaction.shared.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BacenNotificationRepository extends JpaRepository<BacenNotification, Long> {
    List<BacenNotification> findByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime dateTime);
    List<BacenNotification> findByStatusAndLastAttemptAtBefore(NotificationStatus status, LocalDateTime dateTime);
    Optional<BacenNotification> findByIdempotencyKeyAndStatus(String idempotencyKey, NotificationStatus status);
}

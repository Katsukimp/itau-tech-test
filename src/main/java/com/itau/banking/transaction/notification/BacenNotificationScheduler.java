package com.itau.banking.transaction.notification;

import com.itau.banking.transaction.shared.config.BankingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BacenNotificationScheduler {

    private final BacenNotificationService bacenNotificationService;
    private final BankingProperties bankingProperties;

    @Scheduled(cron = "${banking.scheduler.pending-notifications-cron}")
    public void processePendingNotifications() {
        log.debug("[BacenNotificationScheduler].[processePendingNotifications] - Iniciando processamento de notificações pendentes");
        
        try {
            bacenNotificationService.processAllPendingNotifications();
        } catch (Exception e) {
            log.error("[BacenNotificationScheduler].[processePendingNotifications] - Erro ao processar notificações: {}", 
                    e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${banking.scheduler.failed-notifications-cron}")
    public void processeFailedNotifications() {
        log.info("[BacenNotificationScheduler].[processeFailedNotifications] - Iniciando reprocessamento de notificações FAILED");
        
        try {
            bacenNotificationService.processAllFailedNotifications();
        } catch (Exception e) {
            log.error("[BacenNotificationScheduler].[processeFailedNotifications] - Erro ao reprocessar notificações FAILED: {}", 
                    e.getMessage(), e);
        }
    }
}

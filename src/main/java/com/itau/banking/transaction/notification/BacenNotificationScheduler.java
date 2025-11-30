package com.itau.banking.transaction.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BacenNotificationScheduler {

    private final BacenNotificationService bacenNotificationService;

    @Scheduled(cron = "0 * * * * *")
    public void processePendingNotifications() {
        log.debug("[BacenNotificationScheduler].[processePendingNotifications] - Iniciando processamento de notificações pendentes");
        
        try {
            bacenNotificationService.processAllPendingNotifications();
        } catch (Exception e) {
            log.error("[BacenNotificationScheduler].[processePendingNotifications] - Erro ao processar notificações: {}", 
                    e.getMessage(), e);
        }
    }

//    @Scheduled(cron = "0 */30 * * * *")
    @Scheduled(cron = "0 * * * * *") // 1 minuto para testar - Default 30min
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

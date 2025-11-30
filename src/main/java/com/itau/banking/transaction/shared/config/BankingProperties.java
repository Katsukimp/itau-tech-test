package com.itau.banking.transaction.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "banking")
@Getter @Setter
public class BankingProperties {
    
    private Cache cache = new Cache();
    private Notification notification = new Notification();
    private Scheduler scheduler = new Scheduler();
    private Transfer transfer = new Transfer();

    
    @Getter @Setter
    public static class Cache {
        private Customer customer = new Customer();
        private Idempotency idempotency = new Idempotency();
        private DailyLimit dailyLimit = new DailyLimit();

        @Getter @Setter
        public static class Customer {
            private String prefix = "customer:";
            private int ttlHours = 24;
        }
        
        @Getter @Setter
        public static class Idempotency {
            private String prefix = "idempotency:";
            private int ttlHours = 24;
        }
        
        @Getter @Setter
        public static class DailyLimit {
            private String prefix = "daily-limit:";
        }
    }
    
    @Getter @Setter
    public static class Notification {
        private int maxRetryAttempts = 3;
        private int maxFailedAttempts = 10;
        private int failedRetryDelayMinutes = 30;
    }
    
    @Getter @Setter
    public static class Scheduler {
        private String pendingNotificationsCron = "0 * * * * *";
        private String failedNotificationsCron = "0 */30 * * * *";
        private int pendingMinAgeMinutes = 5;
    }

    @Getter @Setter
    public static class Transfer {
        private BigDecimal minimumAmount = BigDecimal.valueOf(1);
    }
}

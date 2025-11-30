package com.itau.banking.transaction.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bacen.mock")
@Getter @Setter
public class BacenMockProperties {
    
    private double failureRate = 0.05;
    private double timeoutRate = 0.02;
    private double rateLimitRate = 0.05;
}

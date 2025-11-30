package com.itau.banking.transaction.integration.customer;

import com.itau.banking.transaction.shared.config.BankingProperties;
import com.itau.banking.transaction.shared.exception.CustomerNotFoundException;
import com.itau.banking.transaction.integration.customer.dto.CustomerDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerApiClient {
    
    private static final Map<Long, CustomerDto> MOCK_CUSTOMERS = new ConcurrentHashMap<>();
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final BankingProperties bankingProperties;
    
    static {
        MOCK_CUSTOMERS.put(1L, CustomerDto.builder()
                .id(1L)
                .name("João da Silva")
                .cpf("123.456.789-00")
                .email("joao.silva@email.com")
                .phone("(11) 98765-4321")
                .build());
        
        MOCK_CUSTOMERS.put(2L, CustomerDto.builder()
                .id(2L)
                .name("Maria Santos")
                .cpf("987.654.321-00")
                .email("maria.santos@email.com")
                .phone("(11) 91234-5678")
                .build());
        
        MOCK_CUSTOMERS.put(3L, CustomerDto.builder()
                .id(3L)
                .name("Pedro Oliveira")
                .cpf("456.789.123-00")
                .email("pedro.oliveira@email.com")
                .phone("(11) 99876-5432")
                .build());
        
        MOCK_CUSTOMERS.put(4L, CustomerDto.builder()
                .id(4L)
                .name("Ana Costa")
                .cpf("321.654.987-00")
                .email("ana.costa@email.com")
                .phone("(11) 94567-8901")
                .build());
        
        MOCK_CUSTOMERS.put(5L, CustomerDto.builder()
                .id(5L)
                .name("Carlos Ferreira")
                .cpf("789.123.456-00")
                .email("carlos.ferreira@email.com")
                .phone("(11) 93456-7890")
                .build());
    }

    public CustomerDto findCustomerById(Long customerId) {
        String cacheKey = bankingProperties.getCache().getCustomer().getPrefix() + customerId;
        CustomerDto cachedCustomer = (CustomerDto) redisTemplate.opsForValue().get(cacheKey);
        if (cachedCustomer != null) {
            log.info("[CustomerApiClient].[findCustomerById] - Cliente {} encontrado no Redis: {}", customerId, cachedCustomer.getName());
            return cachedCustomer;
        }
        
        log.info("[CustomerApiClient].[findCustomerById] - Cliente {} não encontrado no cache, consultando API externa", customerId);
        
        CustomerDto customer = fetchCustomerFromApi(customerId);
        Duration ttl = Duration.ofHours(bankingProperties.getCache().getCustomer().getTtlHours());
        redisTemplate.opsForValue().set(cacheKey, customer, ttl);
        log.info("[CustomerApiClient].[findCustomerById] - Cliente {} salvo no Redis com TTL 24h", customerId);
        
        return customer;
    }
    
    @CircuitBreaker(name = "customerApi", fallbackMethod = "findCustomerByIdFallback")
    @Retry(name = "customerApi")
    private CustomerDto fetchCustomerFromApi(Long customerId) {
        log.info("[CustomerApiClient].[fetchCustomerFromApi] - Consultando API de Cadastro - Cliente ID: {}", customerId);
        
        CustomerDto customer = MOCK_CUSTOMERS.get(customerId);
        if (customer == null) {
            log.warn("[CustomerApiClient].[fetchCustomerFromApi] - Cliente {} não encontrado na API externa", customerId);
            throw new CustomerNotFoundException("Cliente não encontrado: " + customerId);
        }
        
        log.info("[CustomerApiClient].[fetchCustomerFromApi] - Cliente {} encontrado na API externa: {}", customerId, customer.getName());
        return customer;
    }

    private CustomerDto findCustomerByIdFallback(Long customerId, Exception ex) {
        log.error("[CustomerApiClient].[findCustomerByIdFallback] - Erro ao buscar cliente {}: {}", customerId, ex.getMessage());
        throw new CustomerNotFoundException(customerId);
    }
}

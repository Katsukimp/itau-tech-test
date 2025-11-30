package com.itau.banking.transaction.transaction;


import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.account.AccountService;
import com.itau.banking.transaction.integration.customer.CustomerApiClient;
import com.itau.banking.transaction.integration.customer.dto.CustomerDto;
import com.itau.banking.transaction.limit.DailyLimitService;
import com.itau.banking.transaction.notification.BacenNotificationService;
import com.itau.banking.transaction.shared.enums.TransactionStatus;
import com.itau.banking.transaction.shared.enums.TransactionType;
import com.itau.banking.transaction.shared.idempotency.IdempotencyService;
import com.itau.banking.transaction.transaction.dto.TransferRequest;
import com.itau.banking.transaction.transaction.dto.TransferResponse;
import com.itau.banking.transaction.transaction.validator.ValidationStrategyFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final AccountService accountService;
    private final DailyLimitService dailyLimitService;
    private final TransactionRepository transactionRepository;
    private final BacenNotificationService bacenNotificationService;
    private final CustomerApiClient customerApiClient;
    private final ValidationStrategyFactory validationStrategyFactory;
    private final IdempotencyService idempotencyService;

    public TransferResponse transfer(TransferRequest request, String idempotencyKey){
        CustomerDto customer = customerApiClient.findCustomerById(request.sourceAccountId());

        Account sourceAccount = accountService.findById(request.sourceAccountId());
        Account destinationAccount = accountService.findById(request.destinationAccountId());

        validationStrategyFactory.validateAll(sourceAccount, destinationAccount, request.amount());

        Transaction transaction = saveTransaction(customer, sourceAccount, destinationAccount, request.amount(), idempotencyKey);

        try {
            bacenNotificationService.sendSync(transaction, customer);
            log.info("[TransactionService].[transfer] - Notificação BACEN enviada com sucesso (síncrono)");
        } catch (Exception e) {
            log.warn("[TransactionService].[transfer] - Falha no envio síncrono ao BACEN, processamento assíncrono via Kafka/Scheduler: {}", e.getMessage());
        }

        return TransferResponse.builder()
                .transactionId(transaction.getId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .status("SUCCESS")
                .sourceAccount(new TransferResponse.AccountInfo(
                        sourceAccount.getId(),
                        sourceAccount.getAccountNumber(),
                        customer.getName()
                ))
                .destinationAccount(new TransferResponse.AccountInfo(
                        destinationAccount.getId(),
                        destinationAccount.getAccountNumber(),
                        "N/A"
                ))
                .amount(transaction.getAmount())
                .transactionDate(transaction.getCreatedAt())
                .message("Transfer completed successfully")
                .build();
    }

    @Transactional
    public Transaction saveTransaction(CustomerDto customer, Account sourceAccount, Account destinationAccount, BigDecimal amount, String idempotencyKey){
        accountService.debit(sourceAccount, amount);
        accountService.credit(destinationAccount, amount);

        Transaction transaction = new Transaction();
        transaction.setSourceAccount(sourceAccount);
        transaction.setDestinationAccount(destinationAccount);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setTransactionDate(LocalDateTime.now());
        transactionRepository.save(transaction);

        if (idempotencyKey != null) {
            idempotencyService.registerIdempotencyKey(idempotencyKey, transaction.getId());
        }

        dailyLimitService.updateAfterTransfer(sourceAccount.getId(), amount);

        bacenNotificationService.saveOutbox(transaction, customer);
        
        return transaction;
    }
}

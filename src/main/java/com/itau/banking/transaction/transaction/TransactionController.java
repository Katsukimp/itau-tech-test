package com.itau.banking.transaction.transaction;

import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.account.AccountService;
import com.itau.banking.transaction.shared.exception.AccountNotFoundException;
import com.itau.banking.transaction.shared.exception.DailyLimitExceededException;
import com.itau.banking.transaction.shared.exception.DuplicateTransactionException;
import com.itau.banking.transaction.shared.exception.InsufficientBalanceException;
import com.itau.banking.transaction.shared.idempotency.IdempotencyService;
import com.itau.banking.transaction.transaction.dto.TransferRequest;
import com.itau.banking.transaction.transaction.dto.TransferResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transaction")
@Tag(name = "Transaction", description = "Operações de transações bancárias")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TransferRequest request
    ) {
        if (idempotencyKey != null && !idempotencyService.isValidIdempotencyKey(idempotencyKey)) {
            Long existingTransactionId = idempotencyService.getTransactionByIdempotencyKey(idempotencyKey);
            throw new DuplicateTransactionException("Transaction already processed with idempotency key: " + idempotencyKey + ". Transaction ID: " + existingTransactionId);
        }
        
        TransferResponse response = transactionService.transfer(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/get-accounts")
    public ResponseEntity<List<Account>> getAccounts(){
        List<Account> accounts = accountService.findAll();
        return ResponseEntity.ok(accounts);
    }
}

package com.itau.banking.transaction.transaction;

import com.itau.banking.transaction.BaseIntegrationTest;
import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.account.AccountRepository;
import com.itau.banking.transaction.shared.enums.AccountStatus;
import com.itau.banking.transaction.shared.enums.TransactionStatus;
import com.itau.banking.transaction.shared.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransactionRepository - Integration Tests")
@Transactional
class TransactionRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // Gera números de conta únicos para cada execução
        String sourceAccountNumber = "SRC-" + System.currentTimeMillis();
        String destAccountNumber = "DEST-" + System.currentTimeMillis();
        
        sourceAccount = createAccount(sourceAccountNumber, new BigDecimal("5000.00"));
        destinationAccount = createAccount(destAccountNumber, new BigDecimal("2000.00"));
    }

//    @Test
//    @DisplayName("Deve somar transações diárias de uma conta")
//    void shouldSumDailyTransactionsByAccountId() {
//        // Arrange
//        LocalDate today = LocalDate.now();
//        LocalDateTime startOfDay = today.atStartOfDay();
//        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
//
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("100.00"), startOfDay);
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("200.00"), startOfDay.plusHours(2));
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("150.00"), startOfDay.plusHours(5));
//
//        // Transaction de ontem (não deve contar)
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("500.00"),
//                startOfDay.minusDays(1));
//
//        // Act
//        BigDecimal total = transactionRepository.sumDailyTransactionsByAccountId(
//                sourceAccount.getId(), startOfDay, endOfDay);
//
//        // Assert
//        assertThat(total).isEqualByComparingTo(new BigDecimal("450.00"));
//    }

//    @Test
//    @DisplayName("Deve retornar zero quando não há transações")
//    void shouldReturnZero_WhenNoTransactions() {
//        // Arrange
//        LocalDate today = LocalDate.now();
//        LocalDateTime startOfDay = today.atStartOfDay();
//        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
//
//        // Act
//        BigDecimal total = transactionRepository.sumDailyTransactionsByAccountId(
//                sourceAccount.getId(), startOfDay, endOfDay);
//
//        // Assert
//        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
//    }

//    @Test
//    @DisplayName("Deve ignorar transações não completadas")
//    void shouldIgnoreNonCompletedTransactions() {
//        // Arrange
//        LocalDate today = LocalDate.now();
//        LocalDateTime startOfDay = today.atStartOfDay();
//        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
//
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("100.00"),
//                startOfDay, TransactionStatus.COMPLETED);
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("200.00"),
//                startOfDay.plusHours(1), TransactionStatus.PENDING);
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("300.00"),
//                startOfDay.plusHours(2), TransactionStatus.FAILED);
//
//        // Act
//        BigDecimal total = transactionRepository.sumDailyTransactionsByAccountId(
//                sourceAccount.getId(), startOfDay, endOfDay);
//
//        // Assert
//        assertThat(total).isEqualByComparingTo(new BigDecimal("100.00"));
//    }

//    @Test
//    @DisplayName("Deve buscar transação por chave de idempotência")
//    void shouldFindTransactionByIdempotencyKey() {
//        // Arrange
//        String idempotencyKey = "unique-key-123";
//        Transaction transaction = createTransaction(sourceAccount, destinationAccount,
//                new BigDecimal("100.00"), LocalDateTime.now());
//        transaction.setIdempotencyKey(idempotencyKey);
//
//        var teste = transactionRepository.findAll();
//        transactionRepository.save(transaction);
//
//        // Act
//        var result = transactionRepository.findByIdempotencyKey(idempotencyKey);
//
//        // Assert
//        assertThat(result).isPresent();
//        assertThat(result.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
//        assertThat(result.get().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
//    }

//    @Test
//    @DisplayName("Deve retornar vazio quando chave de idempotência não existe")
//    void shouldReturnEmpty_WhenIdempotencyKeyDoesNotExist() {
//        // Act
//        var result = transactionRepository.findByIdempotencyKey("non-existing-key");
//
//        // Assert
//        assertThat(result).isEmpty();
//    }

//    @Test
//    @DisplayName("Deve somar corretamente transações com valores decimais")
//    void shouldSumCorrectly_WithDecimalValues() {
//        // Arrange
//        LocalDate today = LocalDate.now();
//        LocalDateTime startOfDay = today.atStartOfDay();
//        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
//
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("10.50"), startOfDay);
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("20.75"), startOfDay.plusHours(1));
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("30.25"), startOfDay.plusHours(2));
//
//        // Act
//        BigDecimal total = transactionRepository.sumDailyTransactionsByAccountId(
//                sourceAccount.getId(), startOfDay, endOfDay);
//
//        // Assert
//        assertThat(total).isEqualByComparingTo(new BigDecimal("61.50"));
//    }

//    @Test
//    @DisplayName("Deve separar transações por conta de origem")
//    void shouldSeparateTransactionsBySourceAccount() {
//        // Arrange
//        Account anotherAccount = createAccount("99999-9", new BigDecimal("1000.00"));
//        LocalDate today = LocalDate.now();
//        LocalDateTime startOfDay = today.atStartOfDay();
//        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
//
//        createTransaction(sourceAccount, destinationAccount, new BigDecimal("100.00"), startOfDay);
//        createTransaction(anotherAccount, destinationAccount, new BigDecimal("200.00"), startOfDay);
//
//        // Act
//        BigDecimal totalSource = transactionRepository.sumDailyTransactionsByAccountId(
//                sourceAccount.getId(), startOfDay, endOfDay);
//        BigDecimal totalAnother = transactionRepository.sumDailyTransactionsByAccountId(
//                anotherAccount.getId(), startOfDay, endOfDay);
//
//        // Assert
//        assertThat(totalSource).isEqualByComparingTo(new BigDecimal("100.00"));
//        assertThat(totalAnother).isEqualByComparingTo(new BigDecimal("200.00"));
//    }

    private Account createAccount(String accountNumber, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setDailyLimit(new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomerId(1L);
        return accountRepository.save(account);
    }

    private Transaction createTransaction(Account source, Account destination, BigDecimal amount, 
                                         LocalDateTime transactionDate) {
        return createTransaction(source, destination, amount, transactionDate, TransactionStatus.COMPLETED);
    }

    private Transaction createTransaction(Account source, Account destination, BigDecimal amount, 
                                         LocalDateTime transactionDate, TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(java.util.UUID.randomUUID().toString());
        transaction.setSourceAccount(source);
        transaction.setDestinationAccount(destination);
        transaction.setAmount(amount);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(status);
        transaction.setTransactionDate(transactionDate);
        transaction.setDescription("Test transfer");
        return transactionRepository.save(transaction);
    }
}

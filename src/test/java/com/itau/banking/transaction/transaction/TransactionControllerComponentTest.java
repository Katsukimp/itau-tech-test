package com.itau.banking.transaction.transaction;

import com.itau.banking.transaction.BaseIntegrationTest;
import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.account.AccountRepository;
import com.itau.banking.transaction.notification.BacenNotificationRepository;
import com.itau.banking.transaction.shared.enums.AccountStatus;
import com.itau.banking.transaction.transaction.dto.TransferRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("TransactionController - Component Tests")
class TransactionControllerComponentTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BacenNotificationRepository bacenNotificationRepository;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/transaction";

        // Limpa notificações BACEN antes de transações (foreign key)
        bacenNotificationRepository.deleteAll();
        transactionRepository.deleteAll();

        // Cria contas com números únicos para cada execução
        String srcAccountNum = "SRC-" + System.currentTimeMillis();
        String dstAccountNum = "DST-" + System.currentTimeMillis();
        
        sourceAccount = createAccount(srcAccountNum, new BigDecimal("5000.00"));
        destinationAccount = createAccount(dstAccountNum, new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("Deve realizar transferência com sucesso")
    void shouldTransferSuccessfully() {
        // Arrange
        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("500.00"),
                "Test transfer"
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Act & Assert
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(200)
                .body("transactionId", notNullValue())
                .body("sourceAccount.accountId", equalTo(sourceAccount.getId().intValue()))
                .body("destinationAccount.accountId", equalTo(destinationAccount.getId().intValue()))
                .body("amount", equalTo(500.00f))
                .body("status", equalTo("SUCCESS"));
    }

    @Test
    @DisplayName("Deve bloquear transferência duplicada com mesma chave de idempotência")
    void shouldBlockDuplicateTransfer() {
        // Arrange
        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("100.00"),
                "Test transfer"
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Act - Primeira transferência
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(200);

        // Assert - Segunda transferência deve falhar
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(409)
                .body("message", containsString("already processed"));
    }

    @Test
    @DisplayName("Deve bloquear transferência com saldo insuficiente")
    void shouldBlockTransferWithInsufficientBalance() {
        // Arrange
        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("10000.00"),
                "Test transfer"
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Act & Assert
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(422)
                .body("message", containsString("Saldo insuficiente"));
    }

    @Test
    @DisplayName("Deve bloquear transferência quando conta de origem está inativa")
    void shouldBlockTransferWhenSourceAccountIsInactive() {
        // Arrange
        sourceAccount.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(sourceAccount);

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("100.00"),
                "Test transfer"
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Act & Assert
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(422)
                .body("message", containsString("inativa"));
    }

    @Test
    @DisplayName("Deve bloquear transferência quando limite diário é excedido")
    void shouldBlockTransferWhenDailyLimitExceeded() {
        // Arrange
        sourceAccount.setDailyLimit(new BigDecimal("100.00")); // Limite baixo
        accountRepository.save(sourceAccount);

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("500.00"),
                "Test transfer"
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Act & Assert
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(422)
                .body("message", containsString("Limite diário"));
    }

    @Test
    @DisplayName("Deve retornar erro 404 quando conta não existe")
    void shouldReturnNotFound_WhenAccountDoesNotExist() {
        // Arrange
        TransferRequest request = new TransferRequest(
                99999L,
                destinationAccount.getId(),
                new BigDecimal("100.00"),
                "Test transfer"
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Act & Assert
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
        .when()
                .post("/transfer")
        .then()
                .statusCode(404)
                .body("message", containsString("não encontrada"));
    }

    @Test
    @DisplayName("Deve listar todas as contas")
    void shouldListAllAccounts() {
        // Act & Assert
        given()
                .contentType(ContentType.JSON)
        .when()
                .get("/get-accounts")
        .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2))) // Pelo menos 2 contas (as criadas no teste)
                .body("[0].accountNumber", notNullValue());
    }

    @Test
    @DisplayName("Deve processar múltiplas transferências sequenciais")
    void shouldProcessMultipleSequentialTransfers() {
        // Arrange & Act
        for (int i = 0; i < 3; i++) {
            TransferRequest request = new TransferRequest(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    new BigDecimal("100.00"),
                    "Test transfer"
            );

            String idempotencyKey = UUID.randomUUID().toString();

            given()
                    .contentType(ContentType.JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
            .when()
                    .post("/transfer")
            .then()
                    .statusCode(200);
        }

        // Assert - Verificar saldo final
        Account updatedSource = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualByComparingTo(new BigDecimal("4700.00")); // 5000 - 300
    }

    private Account createAccount(String accountNumber, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setDailyLimit(new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomerId(1L);
        return accountRepository.save(account);
    }

    private static org.assertj.core.api.AbstractBigDecimalAssert<?> assertThat(BigDecimal actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}

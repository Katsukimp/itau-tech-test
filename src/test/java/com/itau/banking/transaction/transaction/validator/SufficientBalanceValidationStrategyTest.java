package com.itau.banking.transaction.transaction.validator;

import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.shared.enums.AccountStatus;
import com.itau.banking.transaction.shared.exception.InsufficientBalanceException;
import com.itau.banking.transaction.transaction.validator.concrete.SufficientBalanceValidationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("SufficientBalanceValidationStrategy - Unit Tests")
class SufficientBalanceValidationStrategyTest {

    @InjectMocks
    private SufficientBalanceValidationStrategy validator;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setBalance(new BigDecimal("1000.00"));
        sourceAccount.setStatus(AccountStatus.ACTIVE);

        destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setBalance(new BigDecimal("500.00"));
        destinationAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve validar com sucesso quando saldo é suficiente")
    void shouldValidateSuccessfully_WhenBalanceIsSufficient() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("500.00");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, transferAmount));
    }

    @Test
    @DisplayName("Deve validar com sucesso quando saldo é exatamente igual ao valor")
    void shouldValidateSuccessfully_WhenBalanceIsExactlyEqual() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("1000.00");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, transferAmount));
    }

    @Test
    @DisplayName("Deve lançar exceção quando saldo é insuficiente")
    void shouldThrowException_WhenBalanceIsInsufficient() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("1500.00");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, transferAmount))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    @DisplayName("Deve lançar exceção quando saldo é zero e valor é positivo")
    void shouldThrowException_WhenBalanceIsZero() {
        // Arrange
        sourceAccount.setBalance(BigDecimal.ZERO);
        BigDecimal transferAmount = new BigDecimal("0.01");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, transferAmount))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("Deve validar com sucesso transferência de valor muito pequeno")
    void shouldValidateSuccessfully_WithSmallAmount() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("0.01");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, transferAmount));
    }

    @Test
    @DisplayName("Deve validar com sucesso transferência de valor muito grande")
    void shouldValidateSuccessfully_WithLargeAmount() {
        // Arrange
        sourceAccount.setBalance(new BigDecimal("999999999.99"));
        BigDecimal transferAmount = new BigDecimal("500000000.00");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, transferAmount));
    }
}

package com.itau.banking.transaction.transaction.validator;

import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.shared.enums.AccountStatus;
import com.itau.banking.transaction.shared.exception.InactiveAccountException;
import com.itau.banking.transaction.transaction.validator.concrete.AccountActiveValidationStrategy;
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
@DisplayName("AccountActiveValidationStrategy - Unit Tests")
class AccountActiveValidationStrategyTest {

    @InjectMocks
    private AccountActiveValidationStrategy validator;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setStatus(AccountStatus.ACTIVE);

        destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve validar com sucesso quando ambas contas estão ativas")
    void shouldValidateSuccessfully_WhenBothAccountsAreActive() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.00");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, amount));
    }

    @Test
    @DisplayName("Deve lançar exceção quando conta de origem está inativa")
    void shouldThrowException_WhenSourceAccountIsInactive() {
        // Arrange
        sourceAccount.setStatus(AccountStatus.INACTIVE);
        BigDecimal amount = new BigDecimal("100.00");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, amount))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("Conta de origem inativa");
    }

    @Test
    @DisplayName("Deve lançar exceção quando conta de destino está inativa")
    void shouldThrowException_WhenDestinationAccountIsInactive() {
        // Arrange
        destinationAccount.setStatus(AccountStatus.INACTIVE);
        BigDecimal amount = new BigDecimal("100.00");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, amount))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("Conta de destino inativa");
    }

    @Test
    @DisplayName("Deve lançar exceção quando ambas contas estão inativas")
    void shouldThrowException_WhenBothAccountsAreInactive() {
        // Arrange
        sourceAccount.setStatus(AccountStatus.INACTIVE);
        destinationAccount.setStatus(AccountStatus.INACTIVE);
        BigDecimal amount = new BigDecimal("100.00");

        // Act & Assert - deve falhar na primeira validação (conta origem)
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, amount))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("Conta de origem inativa");
    }

    @Test
    @DisplayName("Deve validar independente do valor da transferência")
    void shouldValidate_RegardlessOfTransferAmount() {
        // Arrange
        BigDecimal zeroAmount = BigDecimal.ZERO;
        BigDecimal largeAmount = new BigDecimal("999999999.99");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, zeroAmount));
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, largeAmount));
    }
}

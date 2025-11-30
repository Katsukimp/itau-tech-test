package com.itau.banking.transaction.transaction.validator;

import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.limit.DailyLimitService;
import com.itau.banking.transaction.shared.enums.AccountStatus;
import com.itau.banking.transaction.shared.exception.DailyLimitExceededException;
import com.itau.banking.transaction.transaction.validator.concrete.DailyLimitValidationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLimitValidationStrategy - Unit Tests")
class DailyLimitValidationStrategyTest {

    @Mock
    private DailyLimitService dailyLimitService;

    @InjectMocks
    private DailyLimitValidationStrategy validator;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setDailyLimit(new BigDecimal("1000.00"));
        sourceAccount.setStatus(AccountStatus.ACTIVE);

        destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve validar com sucesso quando dentro do limite diário")
    void shouldValidateSuccessfully_WhenWithinDailyLimit() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("500.00");
        when(dailyLimitService.canTransfer(eq(1L), any(BigDecimal.class), eq(transferAmount)))
                .thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, transferAmount));
        
        verify(dailyLimitService, times(1))
                .canTransfer(1L, sourceAccount.getDailyLimit(), transferAmount);
    }

    @Test
    @DisplayName("Deve lançar exceção quando limite diário é excedido")
    void shouldThrowException_WhenDailyLimitExceeded() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("1500.00");
        BigDecimal currentTotal = new BigDecimal("800.00");
        
        when(dailyLimitService.canTransfer(eq(1L), any(BigDecimal.class), eq(transferAmount)))
                .thenReturn(false);
        when(dailyLimitService.getCurrentDailyTotal(1L))
                .thenReturn(currentTotal);

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, transferAmount))
                .isInstanceOf(DailyLimitExceededException.class)
                .hasMessageContaining("Limite diário excedido");
        
        verify(dailyLimitService, times(1)).canTransfer(1L, sourceAccount.getDailyLimit(), transferAmount);
        verify(dailyLimitService, times(1)).getCurrentDailyTotal(1L);
    }

    @Test
    @DisplayName("Deve validar quando limite diário é exatamente atingido")
    void shouldValidate_WhenExactlyAtDailyLimit() {
        // Arrange
        BigDecimal transferAmount = new BigDecimal("1000.00");
        when(dailyLimitService.canTransfer(eq(1L), any(BigDecimal.class), eq(transferAmount)))
                .thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, transferAmount));
    }

    @Test
    @DisplayName("Deve usar o limite diário da conta específica")
    void shouldUseDailyLimitFromAccount() {
        // Arrange
        BigDecimal customLimit = new BigDecimal("5000.00");
        sourceAccount.setDailyLimit(customLimit);
        BigDecimal transferAmount = new BigDecimal("500.00");
        
        when(dailyLimitService.canTransfer(eq(1L), eq(customLimit), eq(transferAmount)))
                .thenReturn(true);

        // Act
        validator.validate(sourceAccount, destinationAccount, transferAmount);

        // Assert
        verify(dailyLimitService, times(1))
                .canTransfer(1L, customLimit, transferAmount);
    }

    @Test
    @DisplayName("Deve lançar exceção com informações corretas do limite")
    void shouldThrowExceptionWithCorrectLimitInfo() {
        // Arrange
        BigDecimal accountLimit = new BigDecimal("1000.00");
        BigDecimal transferAmount = new BigDecimal("600.00");
        BigDecimal currentTotal = new BigDecimal("500.00");
        
        sourceAccount.setDailyLimit(accountLimit);
        
        when(dailyLimitService.canTransfer(1L, accountLimit, transferAmount))
                .thenReturn(false);
        when(dailyLimitService.getCurrentDailyTotal(1L))
                .thenReturn(currentTotal);

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(sourceAccount, destinationAccount, transferAmount))
                .isInstanceOf(DailyLimitExceededException.class);
        
        verify(dailyLimitService).getCurrentDailyTotal(1L);
    }

    @Test
    @DisplayName("Deve validar transferências pequenas")
    void shouldValidateSmallTransfers() {
        // Arrange
        BigDecimal smallAmount = new BigDecimal("0.01");
        when(dailyLimitService.canTransfer(eq(1L), any(BigDecimal.class), eq(smallAmount)))
                .thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(sourceAccount, destinationAccount, smallAmount));
    }
}

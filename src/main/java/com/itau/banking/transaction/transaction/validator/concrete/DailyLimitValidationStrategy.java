package com.itau.banking.transaction.transaction.validator.concrete;

import com.itau.banking.transaction.account.Account;
import com.itau.banking.transaction.limit.DailyLimitService;
import com.itau.banking.transaction.transaction.validator.ValidationOrder;
import com.itau.banking.transaction.transaction.validator.ValidationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
@ValidationOrder(3)
public class DailyLimitValidationStrategy implements ValidationStrategy {

    private final DailyLimitService dailyLimitService;

    @Override
    public void validate(Account sourceAccount, Account destinationAccount, BigDecimal amount) {
        log.info("[DailyLimitValidator].[doValidate] - Validando limite diário da conta de origem - Conta: {} - Limite Diário: {} - Valor da Transferência: {}",
                sourceAccount.getId(), sourceAccount.getDailyLimit(), amount);

        if(!dailyLimitService.canTransfer(sourceAccount.getId(), sourceAccount.getDailyLimit(), amount)){
            log.error("[DailyLimitValidator].[doValidate] - Limite diário excedido na conta de origem - Conta: {} - Limite Diário: {} - Valor da Transferência: {}",
                    sourceAccount.getId(), sourceAccount.getDailyLimit(), amount);

            BigDecimal currentTotal = dailyLimitService.getCurrentDailyTotal(sourceAccount.getId());
            throw new com.itau.banking.transaction.shared.exception.DailyLimitExceededException(sourceAccount.getDailyLimit(), currentTotal, amount);
        }

        log.info("[DailyLimitValidator].[doValidate] - Limite diário validado com sucesso na conta de origem");
    }
}

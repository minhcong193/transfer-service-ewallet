package minhtc.vn.transferservice.dto.integration.wallet;


import minhtc.vn.transferservice.enums.WalletFailureCode;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletCreditResult(
        UUID commandId,
        UUID walletTransactionId,
        UUID walletId,
        UUID transferId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String status,
        WalletFailureCode failureCode,
        String failureMessage
) {
}

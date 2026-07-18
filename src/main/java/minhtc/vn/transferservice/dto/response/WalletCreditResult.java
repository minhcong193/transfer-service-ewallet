package minhtc.vn.transferservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletCreditResult(
        UUID commandId,
        UUID walletTransactionId,
        String status,
        BigDecimal availableBalance,
        String failureCode,
        String failureMessage
) {
}

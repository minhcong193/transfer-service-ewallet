package minhtc.vn.transferservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletFinalizeResult(
        UUID commandId,
        UUID walletTransactionId,
        String status,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        String failureCode,
        String failureMessage
) {
}

package minhtc.vn.transferservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditWalletRequest(
        UUID commandId,
        String businessReference,
        BigDecimal amount,
        String currency,
        String transactionType,
        UUID counterpartyWalletId
) {
}

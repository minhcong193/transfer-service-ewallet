package minhtc.vn.transferservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletReservationResult(
        UUID commandId,
        UUID reservationId,
        String status,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        String failureCode,
        String failureMessage
) {
}

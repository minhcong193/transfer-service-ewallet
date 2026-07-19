package minhtc.vn.transferservice.dto.transfer;

import minhtc.vn.transferservice.enums.TransferDirection;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferSearchCriteria(
        TransferStatus status,
        TransferDirection direction,
        LocalDateTime fromDate,
        LocalDateTime toDate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String transferCode
) {

    public TransferDirection resolvedDirection() {
        return direction != null
                ? direction
                : TransferDirection.ALL;
    }
}
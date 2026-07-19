package minhtc.vn.transferservice.dto.transfer;

import minhtc.vn.transferservice.enums.TransferDirection;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferSummaryResponse(
        UUID id,
        String transferCode,
        UUID sourceWalletId,
        UUID targetWalletId,
        TransferDirection direction,
        BigDecimal amount,
        String currency,
        String description,
        TransferStatus status,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
}

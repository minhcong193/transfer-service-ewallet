package minhtc.vn.transferservice.dto.response;

import minhtc.vn.transferservice.enums.TransferDirection;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferSummaryResponse(
        UUID id,
        String transferCode,
        TransferDirection direction,
        UUID counterpartyWalletId,
        String counterpartyDisplayName,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        LocalDateTime createdAt
) {
}

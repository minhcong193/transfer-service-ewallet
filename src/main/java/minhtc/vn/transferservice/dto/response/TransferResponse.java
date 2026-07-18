package minhtc.vn.transferservice.dto.response;

import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String transferCode,
        UUID sourceWalletId,
        UUID targetWalletId,
        String targetDisplayName,
        BigDecimal amount,
        String currency,
        String description,
        TransferStatus status,
        LocalDateTime otpExpiresAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
}

package minhtc.vn.transferservice.dto.transfer;

import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferResponse(

        UUID id,

        String transferCode,

        UUID sourceWalletId,

        UUID targetWalletId,

        BigDecimal amount,

        String currency,

        String description,

        TransferStatus status,

        UUID sourceReservationId,

        UUID sourceReserveTransactionId,

        UUID targetCreditTransactionId,

        UUID sourceFinalizeTransactionId,

        UUID sourceReleaseTransactionId,

        String failureCode,

        String failureMessage,

        LocalDateTime otpExpiresAt,

        LocalDateTime completedAt,

        LocalDateTime compensatedAt,

        LocalDateTime cancelledAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}

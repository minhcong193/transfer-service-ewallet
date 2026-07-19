package minhtc.vn.transferservice.event.payload;

import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferEventPayload(

        UUID transferId,

        String transferCode,

        UUID sourceWalletId,

        UUID targetWalletId,

        UUID sourceOwnerKeycloakUserId,

        UUID targetOwnerKeycloakUserId,

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

        LocalDateTime occurredAt
) {
}
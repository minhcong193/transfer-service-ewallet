package minhtc.vn.transferservice.mapper;

import minhtc.vn.transferservice.dto.transfer.*;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.domain.TransferStatusHistory;
import minhtc.vn.transferservice.enums.TransferDirection;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getTransferCode(),
                transfer.getSourceWalletId(),
                transfer.getTargetWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus(),
                transfer.getSourceReservationId(),
                transfer.getSourceReserveTransactionId(),
                transfer.getTargetCreditTransactionId(),
                transfer.getSourceFinalizeTransactionId(),
                transfer.getSourceReleaseTransactionId(),
                transfer.getFailureCode(),
                transfer.getFailureMessage(),
                transfer.getOtpExpiresAt(),
                transfer.getCompletedAt(),
                transfer.getCompensatedAt(),
                transfer.getCancelledAt(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    public TransferSummaryResponse toSummary(
            Transfer transfer,
            UUID currentUserId
    ) {
        TransferDirection direction =
                transfer.getSourceOwnerKeycloakUserId()
                        .equals(currentUserId)
                        ? TransferDirection.OUT
                        : TransferDirection.IN;

        return new TransferSummaryResponse(
                transfer.getId(),
                transfer.getTransferCode(),
                transfer.getSourceWalletId(),
                transfer.getTargetWalletId(),
                direction,
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus(),
                transfer.getCompletedAt(),
                transfer.getCreatedAt()
        );
    }

    public TransferStatusHistoryResponse toHistory(
            TransferStatusHistory history
    ) {
        return new TransferStatusHistoryResponse(
                history.getId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getReasonCode(),
                history.getReasonMessage(),
                history.getCorrelationId(),
                history.getCreatedAt()
        );
    }
}
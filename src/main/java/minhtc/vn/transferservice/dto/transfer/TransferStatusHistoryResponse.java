package minhtc.vn.transferservice.dto.transfer;

import minhtc.vn.transferservice.enums.TransferStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransferStatusHistoryResponse(
        UUID id,
        TransferStatus fromStatus,
        TransferStatus toStatus,
        String reasonCode,
        String reasonMessage,
        UUID correlationId,
        LocalDateTime createdAt
) {
}
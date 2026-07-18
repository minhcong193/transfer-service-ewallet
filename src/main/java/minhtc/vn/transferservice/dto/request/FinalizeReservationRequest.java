package minhtc.vn.transferservice.dto.request;

import java.util.UUID;

public record FinalizeReservationRequest(
        UUID commandId,
        String businessReference,
        String transactionType,
        UUID counterpartyWalletId
) {
}

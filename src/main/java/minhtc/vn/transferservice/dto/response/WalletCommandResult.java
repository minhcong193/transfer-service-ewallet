package minhtc.vn.transferservice.dto.response;

import java.util.UUID;

public record WalletCommandResult(
        UUID commandId,
        String operation,
        String status,
        UUID walletId,
        String businessReference,
        UUID resultReference,
        String failureCode,
        String failureMessage
) {
}

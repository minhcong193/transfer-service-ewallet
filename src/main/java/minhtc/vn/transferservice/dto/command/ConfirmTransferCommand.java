package minhtc.vn.transferservice.dto.command;

import java.util.UUID;

public record ConfirmTransferCommand(
        UUID transferId,
        String ownerKeycloakUserId,
        String otp,
        String idempotencyKey,
        String requestHash,
        String correlationId
) {

    public ConfirmTransferCommand {
        if (transferId == null) {
            throw new IllegalArgumentException(
                    "transferId must not be null"
            );
        }

        if (ownerKeycloakUserId == null
                || ownerKeycloakUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerKeycloakUserId must not be blank"
            );
        }

        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "OTP must contain exactly 6 digits"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "idempotencyKey must not be blank"
            );
        }

        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException(
                    "requestHash must not be blank"
            );
        }
    }
}

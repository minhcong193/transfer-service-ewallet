package minhtc.vn.transferservice.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferCommand(
        UUID sourceWalletId,
        UUID targetWalletId,
        UUID sourceOwnerKeycloakUserId,
        String sourceUsername,
        BigDecimal amount,
        String currency,
        String description,
        String idempotencyKey,
        String requestHash,
        String correlationId
) {

    public CreateTransferCommand {
        if (sourceWalletId == null) {
            throw new IllegalArgumentException(
                    "sourceWalletId must not be null"
            );
        }

        if (targetWalletId == null) {
            throw new IllegalArgumentException(
                    "targetWalletId must not be null"
            );
        }

        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException(
                    "Source wallet and target wallet must be different"
            );
        }

        if (sourceOwnerKeycloakUserId == null) {
            throw new IllegalArgumentException(
                    "sourceOwnerKeycloakUserId must not be blank"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero"
            );
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "currency must not be blank"
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

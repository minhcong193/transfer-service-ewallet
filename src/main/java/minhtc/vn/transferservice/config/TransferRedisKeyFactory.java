package minhtc.vn.transferservice.config;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Tạo Redis key thống nhất cho Transfer Service.
 *
 * <p>Ví dụ:</p>
 *
 * <pre>
 * transfer-service:otp:challenge:{transferId}
 * transfer-service:otp:cooldown:{transferId}
 * transfer-service:otp:daily-count:{userId}:{yyyy-MM-dd}
 * </pre>
 */
public class TransferRedisKeyFactory {

    private final String keyPrefix;

    public TransferRedisKeyFactory(
            String keyPrefix
    ) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException(
                    "Redis key prefix must not be blank"
            );
        }

        this.keyPrefix = keyPrefix;
    }

    public String otpChallenge(UUID transferId) {
        Objects.requireNonNull(
                transferId,
                "transferId is required"
        );

        return keyPrefix
                + ":otp:challenge:"
                + transferId;
    }

    public String otpCooldown(UUID transferId) {
        Objects.requireNonNull(
                transferId,
                "transferId is required"
        );

        return keyPrefix
                + ":otp:cooldown:"
                + transferId;
    }

    public String otpDailyCount(
            UUID ownerKeycloakUserId,
            LocalDate date
    ) {
        Objects.requireNonNull(
                ownerKeycloakUserId,
                "ownerKeycloakUserId is required"
        );

        Objects.requireNonNull(
                date,
                "date is required"
        );

        return keyPrefix
                + ":otp:daily-count:"
                + ownerKeycloakUserId
                + ":"
                + date;
    }
}

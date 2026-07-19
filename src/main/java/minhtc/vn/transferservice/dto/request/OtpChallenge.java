package minhtc.vn.transferservice.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

public record OtpChallenge(
        UUID challengeId,
        UUID transferId,
        LocalDateTime expiresAt,
        LocalDateTime resendAvailableAt
) {
}

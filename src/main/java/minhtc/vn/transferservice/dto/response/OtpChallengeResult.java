package minhtc.vn.transferservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record OtpChallengeResult(
        UUID transferId,
        LocalDateTime expiresAt,
        LocalDateTime resendAvailableAt
) {
}

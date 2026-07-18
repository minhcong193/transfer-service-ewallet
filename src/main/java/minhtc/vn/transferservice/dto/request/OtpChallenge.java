package minhtc.vn.transferservice.dto.request;

import java.time.LocalDateTime;

public record OtpChallenge(
        String otpHash,
        int attempts,
        int maxAttempts,
        int resendCount,
        LocalDateTime expiresAt,
        LocalDateTime lastSentAt,
        boolean consumed
) {
}

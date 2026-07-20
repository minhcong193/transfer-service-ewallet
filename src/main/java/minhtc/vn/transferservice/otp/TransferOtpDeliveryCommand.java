package minhtc.vn.transferservice.otp;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransferOtpDeliveryCommand(
        UUID ownerKeycloakUserId,
        UUID transferId,
        String transferCode,
        String recipientEmail,
        String recipientName,

        String rawOtp,
        LocalDateTime expiresAt
) {
}
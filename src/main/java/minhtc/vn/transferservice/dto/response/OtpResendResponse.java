package minhtc.vn.transferservice.dto.response;

import minhtc.vn.transferservice.enums.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record OtpResendResponse(
        UUID transferId,
        TransferStatus status,
        Instant otpExpiresAt,
        Instant resendAvailableAt
) {
}

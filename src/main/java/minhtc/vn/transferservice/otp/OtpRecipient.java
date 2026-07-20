package minhtc.vn.transferservice.otp;

import java.util.UUID;

public record OtpRecipient(
        UUID ownerKeycloakUserId,
        String email,
        String displayName
) {
}
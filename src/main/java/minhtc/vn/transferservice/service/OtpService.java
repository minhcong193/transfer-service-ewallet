package minhtc.vn.transferservice.service;

import java.util.UUID;

public interface OtpService {

    OtpChallengeResult createOtp(
            UUID transferId,
            String ownerKeycloakUserId
    );

    OtpVerificationResult verifyOtp(
            UUID transferId,
            String otp
    );

    OtpChallengeResult resendOtp(
            UUID transferId,
            String ownerKeycloakUserId
    );

    void invalidateOtp(UUID transferId);
}

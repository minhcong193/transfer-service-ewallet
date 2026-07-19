package minhtc.vn.transferservice.service;


import minhtc.vn.transferservice.dto.request.OtpChallenge;
import minhtc.vn.transferservice.dto.response.OtpVerificationResult;

import java.util.UUID;

public interface OtpService {

    OtpChallenge createOtp(
            UUID transferId,
            UUID ownerKeycloakUserId
    );

    OtpVerificationResult verifyOtp(
            UUID transferId,
            String otp
    );

    OtpChallenge resendOtp(
            UUID transferId,
            UUID ownerKeycloakUserId
    );

    void invalidateOtp(UUID transferId);
}

package minhtc.vn.transferservice.service;


import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.request.OtpChallenge;
import minhtc.vn.transferservice.dto.response.OtpVerificationResult;
import minhtc.vn.transferservice.otp.OtpRecipient;

import java.util.UUID;

public interface OtpService {

    OtpChallenge createOtp(
            Transfer transfer,
            OtpRecipient recipient
    );

    OtpVerificationResult verifyOtp(
            UUID transferId,
            String rawOtp
    );

    OtpChallenge resendOtp(
            Transfer transfer,
            OtpRecipient recipient
    );

    void deleteOtp(
            UUID transferId
    );
}

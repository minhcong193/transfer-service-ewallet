package minhtc.vn.transferservice.dto.response;

import minhtc.vn.transferservice.enums.OtpVerificationStatus;

public record OtpVerificationResult(
        OtpVerificationStatus status,
        int remainingAttempts
) {

    public boolean verified() {
        return status == OtpVerificationStatus.VERIFIED;
    }
}

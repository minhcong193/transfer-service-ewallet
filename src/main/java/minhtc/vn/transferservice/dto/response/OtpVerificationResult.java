package minhtc.vn.transferservice.dto.response;

public record OtpVerificationResult(
        boolean verified,
        String failureCode,
        int remainingAttempts
) {
}

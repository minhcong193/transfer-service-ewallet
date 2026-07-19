package minhtc.vn.transferservice.exception;

public class OtpChallengeNotFoundException
        extends RuntimeException {

    public OtpChallengeNotFoundException() {
        super("OTP challenge does not exist or has expired");
    }
}

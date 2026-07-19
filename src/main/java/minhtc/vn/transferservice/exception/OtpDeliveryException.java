package minhtc.vn.transferservice.exception;

public class OtpDeliveryException extends RuntimeException {

    public OtpDeliveryException(Throwable cause) {
        super("Cannot deliver transfer OTP", cause);
    }
}

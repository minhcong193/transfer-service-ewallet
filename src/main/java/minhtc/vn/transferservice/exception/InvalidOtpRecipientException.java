package minhtc.vn.transferservice.exception;

public class InvalidOtpRecipientException
        extends RuntimeException {

    public InvalidOtpRecipientException(
            String message
    ) {
        super(message);
    }

    public InvalidOtpRecipientException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
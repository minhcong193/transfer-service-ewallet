package minhtc.vn.transferservice.exception;

import lombok.Getter;

@Getter
public class TransferValidationException
        extends RuntimeException {

    private final String errorCode;

    public TransferValidationException(
            String message
    ) {
        this(
                "TRANSFER_VALIDATION_ERROR",
                message
        );
    }

    public TransferValidationException(
            String errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }

    public TransferValidationException(
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
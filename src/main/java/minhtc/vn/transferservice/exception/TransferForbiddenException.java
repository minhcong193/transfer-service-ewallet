package minhtc.vn.transferservice.exception;

import lombok.Getter;

@Getter
public class TransferForbiddenException
        extends RuntimeException {

    private final String errorCode;

    public TransferForbiddenException(
            String message
    ) {
        super(message);
        this.errorCode = "TRANSFER_FORBIDDEN";
    }
}
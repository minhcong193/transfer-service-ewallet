package minhtc.vn.transferservice.exception;

import lombok.Getter;

@Getter
public class IdempotencyKeyConflictException
        extends RuntimeException {

    private final String errorCode;

    public IdempotencyKeyConflictException(
            String message
    ) {
        super(message);
        this.errorCode = "IDEMPOTENCY_KEY_CONFLICT";
    }
}
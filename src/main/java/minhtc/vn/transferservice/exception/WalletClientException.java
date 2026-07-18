package minhtc.vn.transferservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class WalletClientException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String errorCode;

    public WalletClientException(
            HttpStatusCode statusCode,
            String errorCode,
            String message
    ) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public WalletClientException(
            HttpStatusCode statusCode,
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }
}

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

    /**
     * Lỗi có thể retry an toàn.
     *
     * <p>Các lỗi này thường không chứng minh command thất bại về nghiệp vụ.
     * Saga không được tự chuyển FAILED ngay mà nên giữ trạng thái *_PENDING
     * và để Recovery kiểm tra lại bằng commandId.</p>
     */
    public boolean isRetryable() {
        if (statusCode == null) {
            return true;
        }

        int status = statusCode.value();

        return status == 408
                || status == 425
                || status == 429
                || status >= 500;
    }

    /**
     * Lỗi business rejection rõ ràng từ Wallet Service.
     *
     * <p>Nhóm lỗi này có thể hiểu là Wallet Service đã từ chối command.
     * Tuy nhiên không phải bước nào cũng được xử lý giống nhau:</p>
     *
     * <ul>
     *     <li>Reserve bị reject: transfer có thể FAILED.</li>
     *     <li>Credit bị reject: có thể compensation source reservation.</li>
     *     <li>Finalize bị reject: không release ngay vì target đã credit.</li>
     * </ul>
     */
    public boolean isBusinessRejection() {
        if (statusCode == null) {
            return false;
        }

        int status = statusCode.value();

        return status == 400
                || status == 404
                || status == 409
                || status == 422;
    }

    public boolean isConflict() {
        return statusCode != null
                && statusCode.value() == 409;
    }

    public boolean isNotFound() {
        return statusCode != null
                && statusCode.value() == 404;
    }

    public boolean isValidationError() {
        return statusCode != null
                && statusCode.value() == 400;
    }

    public boolean isUnprocessableEntity() {
        return statusCode != null
                && statusCode.value() == 422;
    }
}
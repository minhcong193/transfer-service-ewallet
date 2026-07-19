package minhtc.vn.transferservice.exception;

/**
 * Exception được ném ra khi có sự cố trong quá trình tích hợp với Dịch vụ Ví (Wallet Service).
 * Điều này có thể do lỗi mạng, phản hồi không mong muốn, hoặc các vấn đề giao tiếp khác.
 */
public class WalletIntegrationException extends RuntimeException {

    public WalletIntegrationException(String message) {
        super(message);
    }

    public WalletIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

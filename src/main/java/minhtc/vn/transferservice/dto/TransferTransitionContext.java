package minhtc.vn.transferservice.dto;

import java.util.UUID;

import java.util.Map;
import java.util.UUID;

/**
 * Context đi kèm mỗi lần Transfer đổi trạng thái.
 *
 * <p>Context này được lưu vào bảng transfer_status_histories để phục vụ:</p>
 *
 * <ul>
 *     <li>Audit trạng thái giao dịch.</li>
 *     <li>Debug Saga.</li>
 *     <li>Truy vết lỗi theo correlationId.</li>
 *     <li>Hiển thị timeline cho admin hoặc FE.</li>
 * </ul>
 */
public record TransferTransitionContext(

        String reasonCode,

        String reasonMessage,

        UUID correlationId
) {

    public TransferTransitionContext {
        reasonCode = normalize(reasonCode);
        reasonMessage = normalize(reasonMessage);
    }

    public static TransferTransitionContext saga(
            String reasonMessage,
            UUID correlationId
    ) {
        return new TransferTransitionContext(
                "SAGA_TRANSITION",
                reasonMessage,
                correlationId
        );
    }

    public static TransferTransitionContext user(
            String reasonMessage,
            UUID correlationId
    ) {
        return new TransferTransitionContext(
                "USER_ACTION",
                reasonMessage,
                correlationId
        );
    }

    public static TransferTransitionContext admin(
            String reasonMessage,
            UUID correlationId
    ) {
        return new TransferTransitionContext(
                "ADMIN_ACTION",
                reasonMessage,
                correlationId
        );
    }

    public static TransferTransitionContext recovery(
            String reasonMessage,
            String errorCode,
            String errorMessage,
            UUID correlationId
    ) {
        return new TransferTransitionContext(
                errorCode != null ? errorCode : "RECOVERY_TRANSITION",
                errorMessage != null ? errorMessage : reasonMessage,
                correlationId
        );
    }

    public static TransferTransitionContext failure(
            String reasonMessage,
            String errorCode,
            String errorMessage,
            UUID correlationId
    ) {
        return new TransferTransitionContext(
                errorCode != null ? errorCode : "TRANSFER_FAILURE",
                errorMessage != null ? errorMessage : reasonMessage,
                correlationId
        );
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isBlank()
                ? null
                : normalized;
    }
}
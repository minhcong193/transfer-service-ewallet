package minhtc.vn.transferservice.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface TransferLimitService {

    void validateAmount(BigDecimal amount);

    /**
     * Chỉ pre-check để báo lỗi sớm.
     * Không thay thế bước reserveDailyLimit.
     */
    void preCheckDailyLimit(
            UUID ownerKeycloakUserId,
            BigDecimal amount
    );

    /**
     * Giữ chỗ hạn mức sau khi OTP đã xác thực.
     */
    void reserveDailyLimit(
            UUID transferId,
            UUID ownerKeycloakUserId,
            BigDecimal amount
    );

    /**
     * Chuyển limit reservation thành completed khi Transfer hoàn tất.
     */
    void completeDailyLimit(UUID transferId);

    /**
     * Hoàn lại hạn mức khi Transfer failed hoặc compensated.
     */
    void releaseDailyLimit(UUID transferId);

    /**
     * Kiểm tra giao dịch có vượt hạn mức hay không.
     *
     * @param ownerKeycloakUserId người thực hiện chuyển tiền
     * @param amount số tiền chuyển
     * @param currency loại tiền
     */
    void validateTransferLimit(
            UUID ownerKeycloakUserId,
            BigDecimal amount,
            String currency
    );
}

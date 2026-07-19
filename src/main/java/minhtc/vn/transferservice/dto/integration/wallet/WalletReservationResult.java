package minhtc.vn.transferservice.dto.integration.wallet;

import minhtc.vn.transferservice.enums.WalletFailureCode;
import minhtc.vn.transferservice.enums.WalletReservationStatus;
import minhtc.vn.transferservice.enums.WalletTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 *
 * @param commandId
 * @param reservationId
 * @param walletTransactionId
 * @param walletId
 * @param transferId
 * @param counterpartyWalletId
 * @param amount
 * @param currency
 * @param availableBalanceBefore
 * @param availableBalanceAfter
 * @param reservedBalanceBefore
 * @param reservedBalanceAfter
 * @param reservationStatus
 * @param transactionStatus
 * @param failureCode
 * @param failureMessage
 * @param createdAt
 */
public record WalletReservationResult(

        /**
         * Command ID của bước reserve.
         *
         * Dùng để Transfer Service đối chiếu response với command đã gửi.
         */
        UUID commandId,

        /**
         * ID bản ghi WalletReservation được tạo.
         *
         * Transfer Service cần lưu lại reservationId này để gọi:
         * finalizeReservation hoặc releaseReservation.
         */
        UUID reservationId,

        /**
         * ID bản ghi WalletTransaction tương ứng với bước reserve.
         *
         * Với reserve, transaction thường có:
         * type = TRANSFER_OUT
         * status = PENDING
         */
        UUID walletTransactionId,

        /**
         * ID ví nguồn bị giữ tiền.
         */
        UUID walletId,

        /**
         * ID transfer aggregate bên Transfer Service.
         */
        UUID transferId,

        /**
         * ID ví đối ứng.
         *
         * Trong bước reserve, đây là ví nhận tiền.
         */
        UUID counterpartyWalletId,

        /**
         * Số tiền đã được reserve.
         */
        BigDecimal amount,

        /**
         * Loại tiền tệ của giao dịch.
         */
        String currency,

        /**
         * availableBalance của ví nguồn trước khi reserve.
         */
        BigDecimal availableBalanceBefore,

        /**
         * availableBalance của ví nguồn sau khi reserve.
         */
        BigDecimal availableBalanceAfter,

        /**
         * reservedBalance của ví nguồn trước khi reserve.
         */
        BigDecimal reservedBalanceBefore,

        /**
         * reservedBalance của ví nguồn sau khi reserve.
         */
        BigDecimal reservedBalanceAfter,

        /**
         * Trạng thái reservation.
         *
         * Sau reserve thành công thường là HELD.
         */
        WalletReservationStatus reservationStatus,

        /**
         * Trạng thái transaction.
         *
         * Sau reserve thành công thường là PENDING,
         * vì tiền mới bị giữ, chưa chính thức rời khỏi ví nguồn.
         */
        WalletTransactionStatus transactionStatus,

        /**
         * Mã lỗi dạng enum để Transfer Service xử lý logic.
         *
         * Thành công thì dùng WalletFailureCode.NONE.
         */
        WalletFailureCode failureCode,

        /**
         * Mô tả lỗi chi tiết hơn để debug/log.
         *
         * Thành công thì có thể null.
         */
        String failureMessage,

        /**
         * Thời điểm tạo reservation.
         */
        LocalDateTime createdAt
) {
}


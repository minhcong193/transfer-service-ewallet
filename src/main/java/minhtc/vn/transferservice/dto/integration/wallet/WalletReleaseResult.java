package minhtc.vn.transferservice.dto.integration.wallet;

import minhtc.vn.transferservice.enums.WalletFailureCode;
import minhtc.vn.transferservice.enums.WalletReservationStatus;
import minhtc.vn.transferservice.enums.WalletTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletReleaseResult(

        /**
         * Command ID của bước release.
         */
        UUID commandId,

        /**
         * ID reservation được release.
         */
        UUID reservationId,

        /**
         * ID transaction TRANSFER_OUT tương ứng.
         *
         * Khi release thành công, transaction này chuyển từ:
         * PENDING -> CANCELLED.
         */
        UUID walletTransactionId,

        /**
         * ID ví nguồn.
         */
        UUID walletId,

        /**
         * ID transfer aggregate bên Transfer Service.
         */
        UUID transferId,

        /**
         * Số tiền được hoàn lại từ reservedBalance về availableBalance.
         *
         * Giá trị này lấy từ WalletReservation.
         */
        BigDecimal amount,

        /**
         * Loại tiền tệ của giao dịch.
         */
        String currency,

        /**
         * availableBalance trước khi release.
         */
        BigDecimal availableBalanceBefore,

        /**
         * availableBalance sau khi release.
         *
         * Khi release:
         * availableBalance += amount
         */
        BigDecimal availableBalanceAfter,

        /**
         * reservedBalance trước khi release.
         */
        BigDecimal reservedBalanceBefore,

        /**
         * reservedBalance sau khi release.
         *
         * Khi release:
         * reservedBalance -= amount
         */
        BigDecimal reservedBalanceAfter,

        /**
         * Trạng thái reservation.
         *
         * Sau release thành công là RELEASED.
         */
        WalletReservationStatus reservationStatus,

        /**
         * Trạng thái transaction.
         *
         * Sau release thành công là CANCELLED.
         */
        WalletTransactionStatus transactionStatus,

        /**
         * Mã lỗi dạng enum.
         *
         * Thành công thì dùng WalletFailureCode.NONE.
         */
        WalletFailureCode failureCode,

        /**
         * Mô tả lỗi chi tiết.
         *
         * Thành công thì có thể null.
         */
        String failureMessage,

        /**
         * Thời điểm release reservation.
         */
        LocalDateTime releasedAt
) {
}

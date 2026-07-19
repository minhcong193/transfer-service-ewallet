package minhtc.vn.transferservice.dto.integration.wallet;

import minhtc.vn.transferservice.enums.WalletFailureCode;
import minhtc.vn.transferservice.enums.WalletReservationStatus;
import minhtc.vn.transferservice.enums.WalletTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletFinalizeResult(

        /**
         * Command ID của bước finalize.
         */
        UUID commandId,

        /**
         * ID reservation được finalize.
         */
        UUID reservationId,

        /**
         * ID transaction TRANSFER_OUT tương ứng.
         *
         * Khi finalize thành công, transaction này chuyển từ:
         * PENDING -> COMPLETED.
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
         * Số tiền được finalize.
         *
         * Giá trị này lấy từ WalletReservation, không lấy từ request.
         */
        BigDecimal amount,

        /**
         * Loại tiền tệ của giao dịch.
         */
        String currency,

        /**
         * availableBalance trước khi finalize.
         *
         * Thường không thay đổi so với sau finalize.
         */
        BigDecimal availableBalanceBefore,

        /**
         * availableBalance sau khi finalize.
         *
         * Finalize không cộng/trừ availableBalance.
         */
        BigDecimal availableBalanceAfter,

        /**
         * reservedBalance trước khi finalize.
         */
        BigDecimal reservedBalanceBefore,

        /**
         * reservedBalance sau khi finalize.
         *
         * Khi finalize:
         * reservedBalance -= amount
         */
        BigDecimal reservedBalanceAfter,

        /**
         * Trạng thái reservation.
         *
         * Sau finalize thành công là FINALIZED.
         */
        WalletReservationStatus reservationStatus,

        /**
         * Trạng thái transaction.
         *
         * Sau finalize thành công là COMPLETED.
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
         * Thời điểm finalize reservation.
         */
        LocalDateTime finalizedAt
) {
}

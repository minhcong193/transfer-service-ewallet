package minhtc.vn.transferservice.dto.integration.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReleaseReservationRequest(

        /**
         * ID định danh duy nhất cho command release.
         *
         * Dùng để đảm bảo idempotency cho bước compensate.
         * Nếu Transfer Service retry release cùng commandId,
         * Wallet Service không được cộng tiền lại lần hai.
         */
        @NotNull
        UUID commandId,

        /**
         * ID transfer aggregate bên Transfer Service.
         *
         * Wallet Service dùng để kiểm tra reservation này có đúng thuộc
         * transfer đang compensate hay không.
         */
        @NotNull
        UUID transferId,

        /**
         * Lý do release reservation.
         *
         * Ví dụ:
         * - CREDIT_TARGET_WALLET_FAILED
         * - TARGET_WALLET_NOT_FOUND
         * - TRANSFER_TIMEOUT
         */
        @Size(max = 500)
        String reason,

        /**
         * ID dùng để trace request xuyên suốt nhiều service.
         */
        UUID correlationId
) {
}

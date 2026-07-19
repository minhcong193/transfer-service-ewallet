package minhtc.vn.transferservice.dto.integration.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FinalizeReservationRequest(

        /**
         * ID định danh duy nhất cho command finalize.
         *
         * Dùng để đảm bảo idempotency cho bước finalize.
         * Nếu retry cùng commandId, Wallet Service không được finalize lần hai.
         */
        @NotNull
        UUID commandId,

        /**
         * ID transfer aggregate bên Transfer Service.
         *
         * Wallet Service dùng để kiểm tra reservation này có đúng thuộc
         * transfer đang finalize hay không.
         */
        @NotNull
        UUID transferId,

        /**
         * Ghi chú khi finalize.
         *
         * Ví dụ: "Credit target wallet completed, finalize source reservation".
         */
        @Size(max = 500)
        String description,

        /**
         * ID dùng để trace request xuyên suốt nhiều service.
         */
        UUID correlationId
) {
}


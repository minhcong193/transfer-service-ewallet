package minhtc.vn.transferservice.dto.integration.wallet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ReserveWalletRequest(

        /**
         * ID định danh duy nhất cho command reserve.
         *
         * Dùng để đảm bảo idempotency cho bước reserve.
         * Nếu Transfer Service retry lại cùng commandId,
         * Wallet Service phải trả về kết quả cũ thay vì trừ tiền lần nữa.
         */
        @NotNull
        UUID commandId,

        /**
         * ID của transfer aggregate bên Transfer Service.
         *
         * Một transferId đại diện cho toàn bộ luồng chuyển tiền:
         * reserve ví nguồn -> credit ví nhận -> finalize ví nguồn.
         */
        @NotNull
        UUID transferId,

        /**
         * ID ví nhận tiền.
         *
         * Khi reserve ví nguồn, trường này được lưu vào
         * WalletTransaction.counterpartyWalletId để biết ví nguồn
         * đang chuyển tiền cho ví nào.
         */
        @NotNull
        UUID targetWalletId,

        /**
         * Số tiền cần giữ trên ví nguồn.
         *
         * Giá trị này sẽ làm:
         * availableBalance -= amount
         * reservedBalance += amount
         */
        @NotNull
        @DecimalMin(value = "0.0001")
        BigDecimal amount,

        /**
         * Loại tiền tệ của giao dịch.
         *
         * Ví dụ: VND, USD.
         * Phải trùng với currency của ví nguồn.
         */
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "INVALID_CURRENCY")
        String currency,

        /**
         * Nội dung chuyển tiền hoặc ghi chú giao dịch.
         *
         * Ví dụ: "Chuyen tien noi bo", "Thanh toan don hang".
         */
        @Size(max = 500)
        String description,

        /**
         * ID dùng để trace request xuyên suốt nhiều service.
         *
         * Giá trị này thường được API Gateway sinh ra và truyền qua:
         * Transfer Service -> Wallet Service.
         */
        UUID correlationId
) {
}


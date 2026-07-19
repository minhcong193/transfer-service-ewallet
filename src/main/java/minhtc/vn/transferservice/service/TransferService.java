package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.request.CancelTransferRequest;
import minhtc.vn.transferservice.dto.request.ConfirmTransferRequest;
import minhtc.vn.transferservice.dto.request.CreateTransferRequest;
import minhtc.vn.transferservice.dto.response.OtpChallengeResult;
import minhtc.vn.transferservice.dto.transfer.TransferResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface TransferService {
    /**
     * Khởi tạo quá trình chuyển tiền và sinh mã xác thực.
     *
     * Luồng xử lý chi tiết:
     * <ul>
     *     <li>1. Đọc Keycloak user ID từ JWT.sub</li>
     *     <li>2. Validate Idempotency-Key</li>
     *     <li>3. Tính requestHash</li>
     *     <li>4. Kiểm tra request đã được xử lý chưa</li>
     *     <li>5. Lấy thông tin source wallet và target wallet</li>
     *     <li>6. Kiểm tra source wallet thuộc user hiện tại</li>
     *     <li>7. Kiểm tra source và target không trùng nhau</li>
     *     <li>8. Validate amount</li>
     *     <li>9. Pre-check hạn mức</li>
     *     <li>10. Tạo Transfer</li>
     *     <li>11. Sinh các Wallet commandId cố định</li>
     *     <li>12. Lưu Transfer với trạng thái OTP_PENDING</li>
     *     <li>13. Tạo OTP trong Redis</li>
     *     <li>14. Ghi Outbox event</li>
     *     <li>15. Trả transferId và otpExpiresAt</li>
     * </ul>
     *
     * @param jwt Token xác thực chứa thông tin user (subject)
     * @param idempotencyKey Khóa chống trùng lặp đảm bảo giao dịch chỉ thực thi 1 lần
     * @param request Dữ liệu đầu vào chứa thông tin chuyển tiền
     * @return DTO chứa transferId và thời điểm hết hạn của mã OTP
     */
    TransferResponse createTransfer(
            Jwt jwt,
            String idempotencyKey,
            CreateTransferRequest request
    );

    /**
     * Xác thực mã OTP và kích hoạt quy trình chuyển tiền (Saga Orchestration).
     *
     * Luồng xử lý chi tiết:
     * <ul>
     *     <li>1. Kiểm tra confirm Idempotency-Key</li>
     *     <li>2. Lock Transfer</li>
     *     <li>3. Kiểm tra source ownership</li>
     *     <li>4. Kiểm tra trạng thái OTP_PENDING</li>
     *     <li>5. Verify OTP atomically</li>
     *     <li>6. Chuyển OTP_PENDING → OTP_VERIFIED</li>
     *     <li>7. Reserve daily limit</li>
     *     <li>8. Gọi TransferSagaService.executeTransfer()</li>
     *     <li>9. Lưu kết quả idempotency</li>
     *     <li>10. Trả response</li>
     * </ul>
     *
     * @param jwt Token xác thực để kiểm tra quyền sở hữu (ownership) đối với giao dịch
//     * @param idempotencyKey Khóa chống trùng lặp dành riêng cho bước xác thực (tránh click đúp gửi OTP nhiều lần)
     * @param request Dữ liệu đầu vào chứa mã giao dịch (transferId) và mã OTP người dùng nhập
     * @return DTO chứa trạng thái mới nhất của giao dịch (ví dụ: PROCESSING)
     */

    TransferResponse confirmTransfer(
            Jwt jwt,
            UUID transferId,
            ConfirmTransferRequest request
    );

    TransferResponse resendOtp(
            Jwt jwt,
            UUID transferId
    );

    TransferResponse cancelTransfer(
            Jwt jwt,
            UUID transferId,
            CancelTransferRequest request
    );
}

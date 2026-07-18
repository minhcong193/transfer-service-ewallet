package minhtc.vn.transferservice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity lưu trữ thông tin giao dịch chuyển tiền nội bộ giữa 2 ví.
 * Được thiết kế để hỗ trợ Distributed Transaction (Saga Pattern).
 */
@Entity
@Table(name = "transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer extends BaseEntity {

    /**
     * Mã giao dịch hiển thị cho người dùng cuối (VD: TRF-20260717-XYZ987).
     * Dùng để tra cứu, đối soát hoặc gọi lên tổng đài hỗ trợ.
     */
    private String transferCode;

    /**
     * Khóa ngoại trỏ đến ID của Ví nguồn (người chuyển).
     */
    private UUID sourceWalletId;

    /**
     * Khóa ngoại trỏ đến ID của Ví đích (người nhận).
     */
    private UUID targetWalletId;

    /**
     * ID của user chuyển tiền (từ hệ thống Keycloak).
     * Lưu tại đây để query lịch sử nhanh mà không cần JOIN sang bảng Wallet.
     */
    private UUID sourceOwnerKeycloakUserId;

    /**
     * ID của user nhận tiền (từ hệ thống Keycloak).
     */
    private UUID targetOwnerKeycloakUserId;

    /**
     * Tên người nhận tại đúng thời điểm chuyển tiền (Snapshot).
     * Bắt buộc phải lưu cứng lại để in sao kê.
     * Nếu sau này user đổi tên thành người khác thì hóa đơn cũ không bị đổi theo.
     */
    private String targetDisplayNameSnapshot;

    /**
     * Số tiền cần chuyển.
     */
    private BigDecimal amount;

    /**
     * Loại tiền tệ (VD: VND, USD).
     */
    private String currency;

    /**
     * Lời nhắn chuyển tiền.
     */
    private String description;

    /**
     * Trạng thái hiện tại của giao dịch (VD: INIT, PENDING_OTP, RESERVED, SUCCESS, FAILED, COMPENSATED).
     */
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    // ========================================================================
    // NHÓM TRƯỜNG BẢO MẬT & TRUY VẾT (SECURITY & OBSERVABILITY)
    // ========================================================================

    /**
     * Khóa chống trùng lặp từ phía Client truyền lên.
     * Đảm bảo nếu user bấm nút "Chuyển" 2 lần liên tiếp thì chỉ có 1 giao dịch được tạo.
     */
    private String createIdempotencyKey;

    /**
     * Mã băm (Hash) của toàn bộ request body.
     * Dùng để kiểm tra tính toàn vẹn, chống hacker can thiệp thay đổi số tiền giữa đường.
     */
    private String requestHash;

    /**
     * ID truy vết luồng xử lý phân tán (Distributed Tracing - e.g., ELK, Jaeger).
     * Truyền xuyên suốt qua các Microservices để trace log.
     */
    private String correlationId;

    /**
     * ID của phiên xác thực OTP (nếu giao dịch vượt hạn mức cần xác thực).
     */
    private UUID otpChallengeId;

    // ========================================================================
    // NHÓM TRƯỜNG PHỤC VỤ SAGA PATTERN (DISTRIBUTED TRANSACTION)
    // ========================================================================

    /**
     * ID của bản ghi tạm giữ tiền (Hold/Freeze) bên ví nguồn.
     */
    private UUID sourceReservationId;

    /**
     * Saga Command 1: Lệnh yêu cầu đóng băng (trừ tạm) tiền từ ví nguồn.
     */
    private UUID sourceReserveCommandId;

    /**
     * Saga Command 2: Lệnh yêu cầu cộng tiền vào ví đích.
     */
    private UUID targetCreditCommandId;

    /**
     * Saga Command 3 (Happy Path): Lệnh chốt (Commit) giao dịch bên ví nguồn
     * (chuyển từ trạng thái đóng băng sang trừ hẳn).
     */
    private UUID sourceFinalizeCommandId;

    /**
     * Saga Command 4 (Rollback Path): Lệnh hoàn tiền (Release/Unfreeze) bên ví nguồn
     * nếu việc cộng tiền vào ví đích (Command 2) bị thất bại.
     */
    private UUID sourceReleaseCommandId;

    // ========================================================================
    // NHÓM TRƯỜNG XỬ LÝ LỖI & RETRY (COMPENSATION)
    // ========================================================================

    /**
     * Mã lỗi nghiệp vụ nếu giao dịch thất bại (VD: ERR_INSUFFICIENT_FUNDS, ERR_TIMEOUT).
     */
    private String failureCode;

    /**
     * Chi tiết lỗi (dùng để log hoặc hiển thị cho user).
     */
    private String failureMessage;

    /**
     * Số lần đã thử Rollback (Compensation) khi xảy ra lỗi.
     * Dùng cho Background Job / Spring Batch quét và retry.
     */
    private Integer compensationAttempts;

    /**
     * Thời điểm dự kiến sẽ chạy lại lệnh Rollback nếu lần trước bị lỗi mạng.
     */
    private LocalDateTime nextRetryAt;

    // ========================================================================
    // NHÓM TRƯỜNG THỜI GIAN (TIMESTAMPS)
    // ========================================================================

    /**
     * Hạn chót để user nhập OTP. Quá thời gian này giao dịch sẽ bị Hủy.
     */
    private LocalDateTime otpExpiresAt;

    /**
     * Thời điểm giao dịch thành công trọn vẹn (Đã cộng tiền cho đích và chốt tiền nguồn).
     */
    private LocalDateTime completedAt;

    /**
     * Thời điểm giao dịch được Rollback (Hoàn tiền về ví nguồn) thành công do gặp lỗi.
     */
    private LocalDateTime compensatedAt;
}

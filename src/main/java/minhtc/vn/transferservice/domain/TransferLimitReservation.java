package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.LimitReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho một "Phiếu tạm giữ hạn mức" (Limit Reservation Receipt) của một giao dịch cụ thể.
 * Được sinh ra khi giao dịch bắt đầu luồng xử lý Saga, và được cập nhật trạng thái (Completed/Released) khi luồng kết thúc.
 */
@Getter
@Entity
@Table(
        name = "transfer_limit_reservations",
        uniqueConstraints = {
                // Ràng buộc duy nhất: Một giao dịch chuyển tiền chỉ được phép có TỐI ĐA MỘT phiếu tạm giữ hạn mức.
                // Chặn đứng lỗi tạo phiếu trùng lặp khi Kafka gửi message Retry.
                @UniqueConstraint(
                        name = "uk_transfer_limit_reservation_transfer",
                        columnNames = "transfer_id"
                )
        }
)
// Ẩn constructor mặc định để ép buộc phải tạo đối tượng thông qua Factory Method (reserve)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferLimitReservation extends BaseEntity{

    /**
     * ID của giao dịch chuyển tiền (Liên kết 1-1 với bảng transfers).
     */
    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    /**
     * Định danh của chủ sở hữu (Người gửi tiền) từ Keycloak.
     */
    @Column(name = "owner_keycloak_user_id", nullable = false)
    private UUID ownerKeycloakUserId;

    /**
     * Ngày sử dụng hạn mức (Rất quan trọng).
     * Mốc thời gian này được chốt cố định lúc tạo phiếu. Nếu giao dịch bị Rollback vào ngày hôm sau,
     * hệ thống vẫn dựa vào mốc này để nhả (release) đúng hạn mức của ngày cũ.
     */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /**
     * Số tiền giao dịch đang bị tạm giữ.
     */
    @Column(
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal amount;

    /**
     * Trạng thái của Phiếu giữ hạn mức:
     * - RESERVED: Đang tạm giữ (chờ xử lý).
     * - COMPLETED: Đã chốt hạ (Giao dịch thành công).
     * - RELEASED: Đã hủy bỏ/Hoàn trả (Giao dịch thất bại).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LimitReservationStatus status;

    /**
     * Factory Method: TẠO PHIẾU TẠM GIỮ.
     * Gọi khi user confirm OTP và trước khi bắn lệnh trừ tiền đi.
     * 
     * @param transferId ID giao dịch
     * @param ownerKeycloakUserId ID người gửi
     * @param usageDate Ngày ghi nhận hạn mức (VD: LocalDate.now(clock))
     * @param amount Số tiền giao dịch
     * @return Phiếu giữ hạn mức ở trạng thái RESERVED
     */
    public static TransferLimitReservation reserve(
            UUID transferId,
            UUID ownerKeycloakUserId,
            LocalDate usageDate,
            BigDecimal amount
    ) {
        TransferLimitReservation reservation = new TransferLimitReservation();

        reservation.transferId = transferId;
        reservation.ownerKeycloakUserId = ownerKeycloakUserId;
        reservation.usageDate = usageDate;
        reservation.amount = amount;
        
        reservation.status = LimitReservationStatus.RESERVED;

        return reservation;
    }

    /**
     * CHỐT PHIẾU (Đánh dấu giao dịch thành công).
     * Thao tác này sẽ chạy song song với hàm complete() bên class TransferDailyLimitUsage.
     * 
     * @param
     */
    public void markCompleted() {
        this.status = LimitReservationStatus.COMPLETED;
    }

    /**
     * HỦY PHIẾU (Đánh dấu giao dịch thất bại và cần nhả hạn mức).
     * Thao tác này sẽ chạy song song với hàm release() bên class TransferDailyLimitUsage.
     * 
     * @param
     */
    public void markReleased() {
        this.status = LimitReservationStatus.RELEASED;
    }
}

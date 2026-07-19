package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity quản lý Hạn mức giao dịch trong ngày của người dùng.
 * Áp dụng cơ chế Hold & Capture (Reserve, Complete, Release) tương đương như quản lý số dư Ví,
 * đảm bảo user không thể lách luật hạn mức bằng cách gửi nhiều request chuyển tiền cùng một lúc.
 */
@Getter
@Entity
@Table(
        name = "transfer_daily_limit_usage",
        uniqueConstraints = {
                // Ràng buộc cốt lõi: Mỗi người dùng chỉ có duy nhất 1 bản ghi theo dõi hạn mức cho mỗi ngày
                @UniqueConstraint(
                        name = "uk_transfer_limit_owner_date",
                        columnNames = {
                                "owner_keycloak_user_id",
                                "usage_date"
                        }
                )
        }
)
// Chặn khởi tạo bừa bãi, bắt buộc thao tác qua các method nghiệp vụ (reserve, complete, release)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferDailyLimitUsage extends BaseEntity{

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    /**
     * Định danh của chủ sở hữu hạn mức (User ID từ Keycloak).
     */
    @Column(name = "owner_keycloak_user_id", nullable = false)
    private UUID ownerKeycloakUserId;

    /**
     * Ngày ghi nhận hạn mức (Thường chốt theo múi giờ chung của toàn hệ thống, VD: UTC).
     */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /**
     * Hạn mức đang bị "đóng băng" (Reserved).
     * Chứa tổng số tiền của các giao dịch đang ở trạng thái chờ xử lý (In-flight/Saga Processing).
     */
    @Column(
            name = "reserved_amount",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal reservedAmount;

    /**
     * Hạn mức đã sử dụng thực tế (Completed).
     * Chứa tổng số tiền của các giao dịch đã chắc chắn chuyển THÀNH CÔNG trong ngày.
     */
    @Column(
            name = "completed_amount",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal completedAmount;

    /**
     * Tính tổng hạn mức đã sử dụng (Bao gồm cả phần đã chốt và phần đang tạm giam).
     * Dùng để Validate lúc user vừa tạo lệnh: Nếu (Transfer Amount + Total) > Limit Ngày -> Từ chối ngay.
     * 
     * @return Tổng số tiền bị hao hụt trong ngày
     */
    public BigDecimal totalUsedAndReserved() {
        return completedAmount.add(reservedAmount);
    }

    /**
     * ĐÓNG BĂNG HẠN MỨC (Hold/Reserve).
     * Gọi khi user confirm OTP thành công và luồng Saga bắt đầu xuất phát.
     * 
     * @param amount Số tiền giao dịch cần đóng băng
     */
    public void reserve(BigDecimal amount) {
        reservedAmount = reservedAmount.add(normalize(amount));
    }

    /**
     * CHỐT HẠN MỨC (Capture/Complete).
     * Gọi khi luồng Saga báo giao dịch đã thành công trọn vẹn (Đã trừ nguồn, cộng đích).
     * Hành động: Mở khóa khoản tiền đang giam và đưa nó vào sổ "Đã sử dụng thực tế".
     * 
     * @param amount Số tiền giao dịch cần chốt
     */
    public void complete(BigDecimal amount) {
        BigDecimal normalizedAmount = normalize(amount);

        // Sanity check: Tiền giam không thể nhỏ hơn khoản định chốt
        if (reservedAmount.compareTo(normalizedAmount) < 0) {
            throw new IllegalStateException("Reserved limit amount is insufficient");
        }

        reservedAmount = reservedAmount.subtract(normalizedAmount);
        completedAmount = completedAmount.add(normalizedAmount);
    }

    /**
     * NHẢ HẠN MỨC (Release/Rollback).
     * Gọi khi luồng Saga gặp lỗi (Ví nguồn không đủ tiền, lỗi mạng, Ví đích bị khóa...).
     * Hành động: Mở khóa khoản tiền đang giam, trả lại "room" hạn mức cho user xài việc khác.
     * 
     * @param amount Số tiền giao dịch cần nhả
     */
    public void release(BigDecimal amount) {
        BigDecimal normalizedAmount = normalize(amount);

        if (reservedAmount.compareTo(normalizedAmount) < 0) {
            throw new IllegalStateException("Reserved limit amount is insufficient");
        }

        reservedAmount = reservedAmount.subtract(normalizedAmount);

    }

    /**
     * Chuẩn hóa độ chính xác của số thập phân.
     * Trong Java, BigDecimal("1.00") không bằng BigDecimal("1.0000").
     * Hàm này fix cứng Scale = 4 để mọi phép tính và so sánh (compareTo) không bao giờ bị lệch.
     */
    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(4, RoundingMode.UNNECESSARY);
    }
}

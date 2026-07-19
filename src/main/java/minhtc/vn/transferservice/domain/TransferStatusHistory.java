package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.TransferStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity lưu trữ Lịch sử chuyển đổi trạng thái của một Giao dịch (Transfer).
 * Phục vụ cho mục đích Audit (kiểm toán), đối soát (Reconciliation) và tra cứu nguyên nhân lỗi
 * trong luồng xử lý phân tán (Saga Orchestration).
 */
@Getter
@Entity
@Table(
        name = "transfer_status_history",
        indexes = {
                // Đánh index ghép (Composite Index) cực kỳ quan trọng.
                // Thường xuyên được dùng để query: "Lấy toàn bộ lịch sử của giao dịch X, sắp xếp theo thời gian".
                @Index(
                        name = "idx_transfer_status_history_transfer",
                        columnList = "transfer_id, created_at"
                )
        }
)
// Ẩn constructor mặc định, bắt buộc tạo đối tượng qua hàm Factory
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferStatusHistory extends BaseEntity{

    /**
     * ID của giao dịch gốc (Liên kết với bảng transfers).
     */
    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    /**
     * Trạng thái cũ trước khi chuyển đổi.
     * Có thể null đối với bản ghi lịch sử đầu tiên (lúc giao dịch vừa được CREATED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private TransferStatus fromStatus;

    /**
     * Trạng thái mới được cập nhật tới.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "to_status",
            nullable = false,
            length = 50
    )
    private TransferStatus toStatus;

    /**
     * Mã lý do (tùy chọn) dẫn đến việc chuyển trạng thái này.
     * Đặc biệt hữu ích khi to_status là FAILED hoặc COMPENSATED
     * (VD: "ERR_WALLET_LOCKED", "ERR_TIMEOUT").
     */
    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    /**
     * Thông báo chi tiết (Human-readable) giải thích lý do chuyển trạng thái.
     * Dùng để show cho admin hoặc CSKH đọc để giải đáp cho khách hàng.
     */
    @Column(name = "reason_message", length = 500)
    private String reasonMessage;

    /**
     * ID dùng để truy vết phân tán (Distributed Tracing).
     * Phải khớp với correlation_id của bảng Transfer. Giúp liên kết log của dòng lịch sử này
     * với các log sinh ra từ các microservices khác (như WalletService, NotificationService).
     */
    @Column(name = "correlation_id")
    private UUID correlationId;


    /**
     * Factory Method để tạo mới một bản ghi lịch sử.
     *
     * @param transferId ID giao dịch
     * @param fromStatus Trạng thái cũ
     * @param toStatus Trạng thái mới
     * @param reasonCode Mã lỗi/Lý do (nếu có)
     * @param reasonMessage Chi tiết lỗi (nếu có)
     * @param correlationId Mã truy vết
     * @return Đối tượng TransferStatusHistory đã được khởi tạo
     */
    public static TransferStatusHistory create(
            UUID transferId,
            TransferStatus fromStatus,
            TransferStatus toStatus,
            String reasonCode,
            String reasonMessage,
            UUID correlationId
    ) {
        TransferStatusHistory history = new TransferStatusHistory();

        history.transferId = transferId;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.reasonCode = reasonCode;
        history.reasonMessage = reasonMessage;
        history.correlationId = correlationId;
        return history;
    }
}

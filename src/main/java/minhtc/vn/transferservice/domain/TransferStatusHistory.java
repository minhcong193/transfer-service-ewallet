package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.util.UUID;

/**
 * Entity lưu trữ lịch sử chuyển đổi trạng thái (State Transition) của một giao dịch chuyển tiền.
 * Phục vụ cho Audit Trail (Lưu vết kiểm toán) và Debug luồng Saga Pattern.
 * Nguyên tắc: Bảng này chỉ có thao tác INSERT (Append-only), tuyệt đối không bao giờ UPDATE hay DELETE.
 */
@Entity
@Table(name = "transfer_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferStatusHistory extends BaseEntity {

    /**
     * ID của giao dịch chuyển tiền gốc.
     * Dùng để JOIN hoặc query toàn bộ vòng đời (lifecycle) của một Transfer cụ thể.
     */
    private UUID transferId;

    /**
     * Trạng thái trước khi thay đổi (Ví dụ: PENDING_OTP).
     * Có thể null nếu đây là record đầu tiên khi giao dịch vừa được khởi tạo (INIT).
     */
    @Enumerated(EnumType.STRING)
    private TransferStatus fromStatus;

    /**
     * Trạng thái mới được cập nhật tới (Ví dụ: RESERVED).
     * Cặp (fromStatus -> toStatus) tạo thành một bước dịch chuyển trong State Machine.
     */
    @Enumerated(EnumType.STRING)
    private TransferStatus toStatus;

    /**
     * Mã code hệ thống giải thích lý do chuyển trạng thái.
     * Rất quan trọng khi chuyển sang trạng thái FAILED hoặc COMPENSATED.
     * VD: "OTP_EXPIRED", "INSUFFICIENT_FUNDS", "TARGET_WALLET_LOCKED", "SUCCESS".
     */
    private String reasonCode;

    /**
     * Thông báo chi tiết của lý do thay đổi trạng thái (dành cho con người đọc).
     * Thường lưu lại message lỗi trả về từ WalletService hoặc thông báo lỗi của Kafka.
     * VD: "Tài khoản nhận đang bị phong tỏa theo yêu cầu số 123".
     */
    private String reasonMessage;

    /**
     * ID truy vết luồng (Distributed Tracing ID).
     * Giúp trả lời câu hỏi: "Sự kiện nhảy trạng thái này bị gây ra bởi Request nào / Command nào?".
     * Khi search ID này trên Kibana/Datadog, bạn sẽ thấy toàn bộ log của các Microservices liên quan.
     */
    private String correlationId;
}

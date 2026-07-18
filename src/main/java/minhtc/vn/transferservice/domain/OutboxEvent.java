package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.OutboxStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity phục vụ Transactional Outbox Pattern.
 * Đảm bảo Guarantee At-Least-Once Delivery (Tin nhắn chắc chắn sẽ được gửi lên Broker
 * kể cả khi mạng rớt hay server sập ngay lúc đang xử lý).
 */
@Entity
@Table(name = "outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent extends BaseEntity {

    /**
     * Loại Aggregate (Thực thể gốc) sinh ra sự kiện này (VD: "TRANSFER", "WALLET").
     */
    private String aggregateType;

    /**
     * ID của thực thể gốc (Chính là UUID của Transfer hoặc Wallet).
     * Thường dùng cái này làm Message Key đẩy lên Kafka để đảm bảo Order Guarantee.
     */
    private UUID aggregateId;

    /**
     * Tên/Loại sự kiện (VD: "TransferCreated", "TransferReserved", "TransferFailed").
     * Thường dùng để Consumer biết cách parse JSON và điều hướng xử lý.
     */
    private String eventType;

    /**
     * Toàn bộ nội dung sự kiện sẽ đẩy lên Kafka (định dạng JSON).
     */
    @Column(columnDefinition = "jsonb")
    private String payload;

    /**
     * Trạng thái của sự kiện Outbox:
     * - PENDING: Mới tạo, chờ đẩy lên Kafka.
     * - PUBLISHED: Đã đẩy thành công lên Kafka.
     * - FAILED: Lỗi không thể đẩy được (VD: Sai format cấu hình).
     */
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    /**
     * Số lần đã thử đẩy lên Kafka (Retry Mechanism).
     */
    private Integer retryCount;

    /**
     * Thời điểm thử lại tiếp theo.
     * Dùng thuật toán Exponential Backoff (VD: thử lại sau 1s, 2s, 4s, 8s...)
     * để tránh spam server Kafka khi nó đang bị quá tải.
     */
    private LocalDateTime nextRetryAt;

    /**
     * Thời điểm thực tế tin nhắn được Kafka xác nhận là đã lưu (ACK).
     */
    private LocalDateTime publishedAt;
}

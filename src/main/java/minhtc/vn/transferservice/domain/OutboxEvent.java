package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.*;
import minhtc.vn.transferservice.enums.OutboxStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity phục vụ Transactional Outbox Pattern.
 * Đảm bảo Guarantee At-Least-Once Delivery (Tin nhắn chắc chắn sẽ được gửi lên Broker
 * kể cả khi mạng rớt hay server sập ngay lúc đang xử lý).
 */
@Getter
@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_events_event_id",
                        columnNames = "event_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_outbox_events_status_next_retry",
                        columnList = "status, next_retry_at, created_at"
                ),
                @Index(
                        name = "idx_outbox_events_aggregate",
                        columnList = "aggregate_type, aggregate_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    /**
     * ID nghiệp vụ duy nhất của event.
     *
     * Không dùng BaseEntity.id làm eventId vì BaseEntity.id chỉ là
     * primary key database.
     */
    @Column(
            name = "event_id",
            nullable = false,
            updatable = false
    )
    private UUID eventId;

    /**
     * Loại aggregate sinh event.
     *
     * Ví dụ: TRANSFER.
     */
    @Column(
            name = "aggregate_type",
            nullable = false,
            length = 50,
            updatable = false
    )
    private String aggregateType;

    /**
     * ID aggregate.
     *
     * Dùng làm Kafka message key để đảm bảo các event cùng transfer
     * được gửi vào cùng partition.
     */
    @Column(
            name = "aggregate_id",
            nullable = false,
            updatable = false
    )
    private UUID aggregateId;

    /**
     * Loại event.
     *
     * Ví dụ:
     * TRANSFER_CREATED,
     * TRANSFER_SOURCE_RESERVED,
     * TRANSFER_COMPLETED.
     */
    @Column(
            name = "event_type",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String eventType;

    /**
     * JSON payload gửi lên Kafka.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "jsonb",
            updatable = false
    )
    private String payload;

    /**
     * Correlation ID phục vụ distributed tracing.
     */
    @Column(
            name = "correlation_id",
            updatable = false
    )
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private OutboxStatus status;

    @Column(
            name = "retry_count",
            nullable = false
    )
    private Integer retryCount;

    /**
     * Thời điểm có thể retry tiếp.
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Thời điểm worker claim event.
     *
     * Dùng như processing lease để tránh nhiều pod publish cùng event.
     */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * Thời điểm Kafka ACK thành công.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * Lỗi publish gần nhất.
     */
    @Column(
            name = "last_error",
            length = 1000
    )
    private String lastError;

    public static OutboxEvent create(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            UUID correlationId
    ) {
        OutboxEvent event = new OutboxEvent();

        event.eventId = Objects.requireNonNull(
                eventId,
                "eventId is required"
        );

        event.aggregateType =
                requireText(
                        aggregateType,
                        "aggregateType"
                );

        event.aggregateId = Objects.requireNonNull(
                aggregateId,
                "aggregateId is required"
        );

        event.eventType =
                requireText(
                        eventType,
                        "eventType"
                );

        event.payload =
                requireText(
                        payload,
                        "payload"
                );

        event.correlationId = correlationId;

        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;

        return event;
    }

    /**
     * Worker claim event trước khi publish.
     */
    public void markProcessing(
            LocalDateTime processingStartedAt
    ) {
        this.status = OutboxStatus.PROCESSING;

        this.processingStartedAt =
                Objects.requireNonNull(
                        processingStartedAt,
                        "processingStartedAt is required"
                );
    }

    /**
     * Kafka đã ACK.
     */
    public void markPublished(
            LocalDateTime publishedAt
    ) {
        this.status = OutboxStatus.PUBLISHED;

        this.publishedAt =
                Objects.requireNonNull(
                        publishedAt,
                        "publishedAt is required"
                );

        this.processingStartedAt = null;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    /**
     * Publish lỗi nhưng vẫn có thể retry.
     */
    public void markRetry(
            String errorMessage,
            LocalDateTime nextRetryAt
    ) {
        this.status = OutboxStatus.PENDING;

        this.retryCount =
                this.retryCount != null
                        ? this.retryCount + 1
                        : 1;

        this.lastError =
                truncate(errorMessage, 1000);

        this.nextRetryAt =
                Objects.requireNonNull(
                        nextRetryAt,
                        "nextRetryAt is required"
                );

        this.processingStartedAt = null;
    }

    /**
     * Không retry nữa.
     */
    public void markFailed(
            String errorMessage
    ) {
        this.status = OutboxStatus.FAILED;

        this.retryCount =
                this.retryCount != null
                        ? this.retryCount + 1
                        : 1;

        this.lastError =
                truncate(errorMessage, 1000);

        this.nextRetryAt = null;
        this.processingStartedAt = null;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        return value.trim();
    }

    private static String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}

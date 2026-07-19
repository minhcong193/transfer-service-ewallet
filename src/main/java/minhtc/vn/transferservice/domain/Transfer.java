package minhtc.vn.transferservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.TransferStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Đại diện cho một giao dịch chuyển tiền nội bộ giữa hai ví.
 *
 * <p>Transfer Service chỉ điều phối giao dịch. Số dư thực tế vẫn được
 * quản lý bởi Wallet Service thông qua các command:</p>
 *
 * <ul>
 *     <li>Reserve số dư ví nguồn.</li>
 *     <li>Credit ví đích.</li>
 *     <li>Finalize reservation của ví nguồn.</li>
 *     <li>Release reservation khi cần compensation.</li>
 * </ul>
 *
 * <p>Mỗi Wallet command có một command ID cố định. Khi retry, Transfer
 * Service phải sử dụng lại đúng command ID cũ để Wallet Service xử lý
 * idempotency.</p>
 */
@Getter
@Entity
@Table(
        name = "transfers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transfers_transfer_code",
                        columnNames = "transfer_code"
                ),
                @UniqueConstraint(
                        name = "uk_transfers_owner_idempotency",
                        columnNames = {
                                "source_owner_keycloak_user_id",
                                "request_idempotency_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_transfers_source_owner_created_at",
                        columnList =
                                "source_owner_keycloak_user_id, created_at"
                ),
                @Index(
                        name = "idx_transfers_target_owner_created_at",
                        columnList =
                                "target_owner_keycloak_user_id, created_at"
                ),
                @Index(
                        name = "idx_transfers_source_wallet_created_at",
                        columnList =
                                "source_wallet_id, created_at"
                ),
                @Index(
                        name = "idx_transfers_target_wallet_created_at",
                        columnList =
                                "target_wallet_id, created_at"
                ),
                @Index(
                        name = "idx_transfers_status_next_retry",
                        columnList =
                                "status, next_retry_at, updated_at"
                ),
                @Index(
                        name = "idx_transfers_correlation_id",
                        columnList = "correlation_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer extends BaseEntity {

    private static final int MONEY_SCALE = 4;

    /**
     * Mã giao dịch hiển thị cho user và bộ phận vận hành.
     *
     * <p>Ví dụ: TRF-20260719-ABC123.</p>
     */
    @Column(
            name = "transfer_code",
            nullable = false,
            length = 50,
            updatable = false
    )
    private String transferCode;

    /**
     * Ví bị trừ tiền.
     */
    @Column(
            name = "source_wallet_id",
            nullable = false,
            updatable = false
    )
    private UUID sourceWalletId;

    /**
     * Ví nhận tiền.
     */
    @Column(
            name = "target_wallet_id",
            nullable = false,
            updatable = false
    )
    private UUID targetWalletId;

    /**
     * Keycloak user ID của chủ ví nguồn.
     *
     * <p>Field này được lưu vào Transfer để kiểm tra ownership mà không
     * cần gọi lại Wallet Service khi xem hoặc xác nhận giao dịch.</p>
     */
    @Column(
            name = "source_owner_keycloak_user_id",
            nullable = false,
            updatable = false
    )
    private UUID sourceOwnerKeycloakUserId;

    /**
     * Keycloak user ID của chủ ví đích.
     */
    @Column(
            name = "target_owner_keycloak_user_id",
            nullable = false,
            updatable = false
    )
    private UUID targetOwnerKeycloakUserId;

    /**
     * Số tiền chuyển.
     */
    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = MONEY_SCALE,
            updatable = false
    )
    private BigDecimal amount;

    /**
     * Loại tiền tệ theo mã ISO 4217.
     *
     * <p>Ví dụ: VND, USD.</p>
     */
    @Column(
            name = "currency",
            nullable = false,
            length = 3,
            updatable = false
    )
    private String currency;

    /**
     * Nội dung chuyển tiền do người dùng nhập.
     */
    @Column(
            name = "description",
            length = 255,
            updatable = false
    )
    private String description;

    /**
     * Trạng thái hiện tại của Transfer Saga.
     *
     * <p>Không nên sửa trực tiếp field này. Mọi thay đổi trạng thái phải
     * đi qua TransferStateMachineService.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 50
    )
    private TransferStatus status;

    // ========================================================================
    // Public request idempotency
    // ========================================================================

    /**
     * Idempotency-Key của request tạo transfer.
     *
     * <p>Unique theo chủ ví nguồn để cùng một user không tạo hai transfer
     * từ cùng một request retry.</p>
     */
    @Column(
            name = "request_idempotency_key",
            nullable = false,
            updatable = false
    )
    private UUID requestIdempotencyKey;

    /**
     * SHA-256 của payload tạo transfer đã được chuẩn hóa.
     *
     * <p>Nếu cùng Idempotency-Key nhưng requestHash khác nhau thì trả lỗi
     * IDEMPOTENCY_KEY_CONFLICT.</p>
     */
    @Column(
            name = "request_hash",
            nullable = false,
            length = 64,
            updatable = false
    )
    private String requestHash;

    /**
     * Correlation ID dùng để liên kết log giữa API Gateway,
     * Transfer Service, Wallet Service và Monitoring Service.
     */
    @Column(
            name = "correlation_id",
            updatable = false
    )
    private UUID correlationId;

    // ========================================================================
    // OTP information
    // ========================================================================

    /**
     * ID của OTP challenge hiện tại.
     *
     * <p>OTP plaintext và OTP hash không được lưu trong bảng transfers.
     * OTP hash được lưu trong Redis.</p>
     */
    @Column(name = "otp_challenge_id")
    private UUID otpChallengeId;

    /**
     * Thời điểm OTP hết hạn, dùng để hiển thị cho FE và audit.
     *
     * <p>Redis TTL vẫn là nguồn kiểm soát OTP thực tế.</p>
     */
    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    /**
     * Thời điểm OTP được xác nhận thành công.
     */
    @Column(name = "otp_verified_at")
    private LocalDateTime otpVerifiedAt;

    // ========================================================================
    // Wallet command IDs
    // ========================================================================

    /**
     * Command ID dùng khi reserve tiền tại ví nguồn.
     *
     * <p>ID này được tạo đúng một lần khi tạo Transfer.</p>
     */
    @Column(
            name = "source_reserve_command_id",
            nullable = false,
            updatable = false
    )
    private UUID sourceReserveCommandId;

    /**
     * Command ID dùng khi credit tiền vào ví đích.
     */
    @Column(
            name = "target_credit_command_id",
            nullable = false,
            updatable = false
    )
    private UUID targetCreditCommandId;

    /**
     * Command ID dùng khi finalize reservation ở ví nguồn.
     */
    @Column(
            name = "source_finalize_command_id",
            nullable = false,
            updatable = false
    )
    private UUID sourceFinalizeCommandId;

    /**
     * Command ID dùng khi release reservation để compensation.
     */
    @Column(
            name = "source_release_command_id",
            nullable = false,
            updatable = false
    )
    private UUID sourceReleaseCommandId;

// ========================================================================
// Wallet command results
// ========================================================================

    /**
     * Reservation ID do Wallet Service trả về sau khi reserve thành công.
     *
     * <p>ID này được dùng làm path variable cho bước finalize hoặc release.</p>
     */
    @Column(name = "source_reservation_id")
    private UUID sourceReservationId;

    /**
     * Wallet transaction ID được tạo khi reserve ví nguồn.
     *
     * <p>Thông thường đây là transaction TRANSFER_OUT ở trạng thái PENDING
     * hoặc RESERVED. Khi finalize, Wallet Service có thể cập nhật chính
     * transaction này sang COMPLETED.</p>
     */
    @Column(name = "source_reserve_transaction_id")
    private UUID sourceReserveTransactionId;

    /**
     * Wallet transaction ID của bước credit ví đích.
     *
     * <p>Đây là transaction TRANSFER_IN của target wallet.</p>
     */
    @Column(name = "target_credit_transaction_id")
    private UUID targetCreditTransactionId;

    /**
     * Wallet transaction ID trả về từ bước finalize.
     *
     * <p>Giá trị này có thể bằng sourceReserveTransactionId nếu Wallet Service
     * cập nhật transaction reserve hiện có, hoặc là ID mới nếu Wallet Service
     * tạo transaction finalize riêng.</p>
     */
    @Column(name = "source_finalize_transaction_id")
    private UUID sourceFinalizeTransactionId;

    /**
     * Wallet transaction ID trả về từ bước release.
     *
     * <p>Có thể bằng sourceReserveTransactionId nếu Wallet Service chỉ cập nhật
     * transaction reserve thành CANCELLED/REVERSED.</p>
     */
    @Column(name = "source_release_transaction_id")
    private UUID sourceReleaseTransactionId;

    // ========================================================================
    // Business milestone timestamps
    // ========================================================================

    @Column(name = "source_reserved_at")
    private LocalDateTime sourceReservedAt;

    @Column(name = "target_credited_at")
    private LocalDateTime targetCreditedAt;

    @Column(name = "source_finalized_at")
    private LocalDateTime sourceFinalizedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensated_at")
    private LocalDateTime compensatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Thời điểm Wallet Service xác nhận reservation đã được release.
     */
    @Column(name = "source_released_at")
    private LocalDateTime sourceReleasedAt;

    // ========================================================================
    // Terminal business failure
    // ========================================================================

    /**
     * Mã lỗi nghiệp vụ cuối cùng làm giao dịch thất bại.
     *
     * <p>Ví dụ: INSUFFICIENT_BALANCE, WALLET_INACTIVE.</p>
     */
    @Column(
            name = "failure_code",
            length = 100
    )
    private String failureCode;

    /**
     * Mô tả lỗi nghiệp vụ cuối cùng.
     */
    @Column(
            name = "failure_message",
            length = 500
    )
    private String failureMessage;

    // ========================================================================
    // Recovery and compensation
    // ========================================================================

    /**
     * Số lần Recovery xử lý trạng thái chưa xác định.
     */
    @Column(
            name = "recovery_attempts",
            nullable = false
    )
    private Integer recoveryAttempts;

    /**
     * Số lần retry compensation.
     */
    @Column(
            name = "compensation_attempts",
            nullable = false
    )
    private Integer compensationAttempts;

    /**
     * Thời điểm sớm nhất Recovery Scheduler được xử lý lại Transfer.
     *
     * <p>Field này cũng được dùng làm processing lease khi nhiều pod cùng
     * chạy Recovery Scheduler.</p>
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Mã lỗi gần nhất gặp trong quá trình gọi Wallet Service hoặc Recovery.
     *
     * <p>Khác với failureCode: lastErrorCode có thể là lỗi tạm thời như
     * timeout và chưa làm Transfer thất bại.</p>
     */
    @Column(
            name = "last_error_code",
            length = 100
    )
    private String lastErrorCode;

    /**
     * Chi tiết lỗi gần nhất.
     */
    @Column(
            name = "last_error_message",
            length = 500
    )
    private String lastErrorMessage;

    /**
     * Tạo một Transfer mới ở trạng thái CREATED.
     *
     * <p>ID của Transfer không được truyền vào vì được sinh bởi BaseEntity
     * bằng UUID version 7 khi entity được persist.</p>
     */
    public static Transfer create(
            String transferCode,
            UUID sourceWalletId,
            UUID targetWalletId,
            UUID sourceOwnerKeycloakUserId,
            UUID targetOwnerKeycloakUserId,
            BigDecimal amount,
            String currency,
            String description,
            UUID requestIdempotencyKey,
            String requestHash,
            UUID correlationId
    ) {
        validateCreateArguments(
                transferCode,
                sourceWalletId,
                targetWalletId,
                sourceOwnerKeycloakUserId,
                targetOwnerKeycloakUserId,
                amount,
                currency,
                requestIdempotencyKey,
                requestHash
        );

        if (sourceWalletId.equals(targetWalletId)) {
            throw new IllegalArgumentException(
                    "Source wallet and target wallet must be different"
            );
        }

        Transfer transfer = new Transfer();

        transfer.transferCode = transferCode.trim();

        transfer.sourceWalletId = sourceWalletId;
        transfer.targetWalletId = targetWalletId;

        transfer.sourceOwnerKeycloakUserId =
                sourceOwnerKeycloakUserId;

        transfer.targetOwnerKeycloakUserId =
                targetOwnerKeycloakUserId;

        transfer.amount = normalizeAmount(amount);

        transfer.currency = currency
                .trim()
                .toUpperCase(Locale.ROOT);

        transfer.description =
                normalizeNullableText(description);

        transfer.requestIdempotencyKey =
                requestIdempotencyKey;

        transfer.requestHash =
                requestHash.trim();

        transfer.correlationId = correlationId;

        /*
         * Mỗi command ID được tạo đúng một lần.
         *
         * Retry bắt buộc sử dụng lại các giá trị này.
         */
        transfer.sourceReserveCommandId =
                UUID.randomUUID();

        transfer.targetCreditCommandId =
                UUID.randomUUID();

        transfer.sourceFinalizeCommandId =
                UUID.randomUUID();

        transfer.sourceReleaseCommandId =
                UUID.randomUUID();

        transfer.status = TransferStatus.CREATED;

        transfer.recoveryAttempts = 0;
        transfer.compensationAttempts = 0;

        return transfer;
    }

    /**
     * Gắn OTP challenge mới vào Transfer.
     *
     * <p>Được gọi khi tạo OTP lần đầu hoặc resend OTP.</p>
     */
    public void attachOtpChallenge(
            UUID challengeId,
            LocalDateTime expiresAt
    ) {
        this.otpChallengeId = Objects.requireNonNull(
                challengeId,
                "challengeId is required"
        );

        this.otpExpiresAt = Objects.requireNonNull(
                expiresAt,
                "expiresAt is required"
        );
    }

    /**
     * Thay đổi trạng thái Transfer.
     *
     * <p>Method này chỉ nên được gọi bởi TransferStateMachineService.</p>
     */
    public void changeStatus(
            TransferStatus targetStatus,
            LocalDateTime changedAt
    ) {
        Objects.requireNonNull(
                targetStatus,
                "targetStatus is required"
        );

        Objects.requireNonNull(
                changedAt,
                "changedAt is required"
        );

        this.status = targetStatus;

        switch (targetStatus) {
            case OTP_VERIFIED ->
                    this.otpVerifiedAt = changedAt;

            case SOURCE_RESERVED ->
                    this.sourceReservedAt = changedAt;

            case TARGET_CREDITED ->
                    this.targetCreditedAt = changedAt;

            case SOURCE_FINALIZED ->
                    this.sourceFinalizedAt = changedAt;

            case COMPLETED -> {
                this.completedAt = changedAt;

                /*
                 * Giao dịch hoàn tất thành công nên xóa thông tin lỗi
                 * terminal và lỗi recovery trước đó.
                 */
                this.failureCode = null;
                this.failureMessage = null;

                clearRecoveryState();
            }

            case COMPENSATED -> {
                this.compensatedAt = changedAt;
                clearRecoveryState();
            }

            case CANCELLED ->
                    this.cancelledAt = changedAt;

            default -> {
                /*
                 * Các status còn lại không có timestamp chuyên biệt.
                 */
            }
        }
    }

    /**
     * Lưu kết quả reserve thành công từ Wallet Service.
     */
    public void applySourceReservation(
            UUID reservationId,
            UUID walletTransactionId,
            LocalDateTime reservedAt
    ) {
        this.sourceReservationId =
                Objects.requireNonNull(
                        reservationId,
                        "reservationId is required"
                );

        this.sourceReserveTransactionId =
                Objects.requireNonNull(
                        walletTransactionId,
                        "walletTransactionId is required"
                );

        this.sourceReservedAt =
                Objects.requireNonNull(
                        reservedAt,
                        "reservedAt is required"
                );

        clearRecoveryState();
    }

    /**
     * Lưu kết quả credit thành công vào ví đích.
     */
    public void applyTargetCreditResult(
            UUID walletTransactionId,
            LocalDateTime creditedAt
    ) {
        this.targetCreditTransactionId =
                Objects.requireNonNull(
                        walletTransactionId,
                        "walletTransactionId is required"
                );

        this.targetCreditedAt =
                Objects.requireNonNull(
                        creditedAt,
                        "creditedAt is required"
                );

        clearRecoveryState();
    }

    /**
     * Lưu kết quả release reservation khi compensation thành công.
     */
    public void applySourceReleaseResult(
            UUID walletTransactionId,
            LocalDateTime releasedAt
    ) {
        /*
         * Tùy thiết kế Wallet Service, release có thể không tạo transaction
         * mới mà chỉ cập nhật transaction reserve hiện có.
         *
         * Vì WalletReleaseResult hiện có walletTransactionId nên vẫn lưu lại
         * để phục vụ đối soát. Không bắt buộc non-null nếu Wallet Service cho
         * phép release mà không có transaction.
         */
        this.sourceReleaseTransactionId =
                walletTransactionId;

        this.sourceReleasedAt =
                Objects.requireNonNull(
                        releasedAt,
                        "releasedAt is required"
                );

        clearRecoveryState();
    }

    /**
     * Lưu kết quả finalize reservation của ví nguồn.
     */
    public void applySourceFinalizeResult(
            UUID walletTransactionId,
            LocalDateTime finalizedAt
    ) {
        this.sourceFinalizeTransactionId =
                Objects.requireNonNull(
                        walletTransactionId,
                        "walletTransactionId is required"
                );

        this.sourceFinalizedAt =
                Objects.requireNonNull(
                        finalizedAt,
                        "finalizedAt is required"
                );

        clearRecoveryState();
    }

    /**
     * Ghi lỗi nghiệp vụ terminal.
     *
     * <p>Được sử dụng khi Transfer chuyển sang FAILED hoặc
     * MANUAL_REVIEW.</p>
     */
    public void markFailure(
            String failureCode,
            String failureMessage
    ) {
        this.failureCode =
                normalizeNullableText(failureCode);

        this.failureMessage =
                truncate(
                        normalizeNullableText(
                                failureMessage
                        ),
                        500
                );
    }

    /**
     * Ghi lỗi gần nhất trong quá trình Saga hoặc Recovery.
     *
     * <p>Lỗi này có thể chỉ là lỗi tạm thời và chưa làm Transfer
     * thất bại.</p>
     */
    public void recordLastError(
            String errorCode,
            String errorMessage
    ) {
        this.lastErrorCode =
                truncate(
                        normalizeNullableText(errorCode),
                        100
                );

        this.lastErrorMessage =
                truncate(
                        normalizeNullableText(errorMessage),
                        500
                );
    }

    /**
     * Lên lịch Recovery cho trạng thái hiện tại.
     */
    public void scheduleRecovery(
            String errorCode,
            String errorMessage,
            LocalDateTime retryAt
    ) {
        this.recoveryAttempts =
                safeValue(this.recoveryAttempts) + 1;

        recordLastError(
                errorCode,
                errorMessage
        );

        this.nextRetryAt =
                Objects.requireNonNull(
                        retryAt,
                        "retryAt is required"
                );
    }

    /**
     * Lên lịch retry compensation.
     */
    public void scheduleCompensationRetry(
            String errorCode,
            String errorMessage,
            LocalDateTime retryAt
    ) {
        this.compensationAttempts =
                safeValue(this.compensationAttempts) + 1;

        recordLastError(
                errorCode,
                errorMessage
        );

        this.nextRetryAt =
                Objects.requireNonNull(
                        retryAt,
                        "retryAt is required"
                );
    }

    /**
     * Đặt processing lease trước khi Recovery gọi Wallet Service.
     *
     * <p>Trong thời gian lease, pod khác không claim lại Transfer này.</p>
     */
    public void acquireRecoveryLease(
            LocalDateTime leaseUntil
    ) {
        this.nextRetryAt =
                Objects.requireNonNull(
                        leaseUntil,
                        "leaseUntil is required"
                );
    }

    /**
     * Xóa trạng thái Recovery sau khi một bước Saga đã được xác nhận
     * thành công.
     *
     * <p>recoveryAttempts được reset để mỗi bước Saga có số lần retry
     * riêng.</p>
     */
    public void clearRecoveryState() {
        this.recoveryAttempts = 0;
        this.nextRetryAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /**
     * Xóa lỗi gần nhất nhưng giữ nguyên số lần recovery.
     */
    public void clearLastError() {
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /**
     * Kiểm tra user có phải chủ ví nguồn không.
     *
     * <p>Dùng cho confirm OTP, resend OTP và cancel transfer.</p>
     */
    public boolean isOwnedBySource(UUID userId) {
        return userId != null
                && sourceOwnerKeycloakUserId.equals(userId);
    }

    /**
     * Kiểm tra user có liên quan đến transfer không.
     *
     * <p>Chủ ví nguồn và chủ ví đích đều có quyền xem transfer.</p>
     */
    public boolean isRelatedTo(UUID userId) {
        if (userId == null) {
            return false;
        }

        return sourceOwnerKeycloakUserId.equals(userId)
                || targetOwnerKeycloakUserId.equals(userId);
    }

    /**
     * Kiểm tra Transfer đang ở trạng thái terminal.
     */
    public boolean isTerminal() {
        return switch (status) {
            case COMPLETED,
                 FAILED,
                 COMPENSATED,
                 CANCELLED,
                 MANUAL_REVIEW -> true;

            default -> false;
        };
    }

    /**
     * Kiểm tra Transfer đang chờ Recovery.
     */
    public boolean isRecoveryPending() {
        return switch (status) {
            case SOURCE_RESERVE_PENDING,
                 TARGET_CREDIT_PENDING,
                 SOURCE_FINALIZE_PENDING,
                 COMPENSATION_PENDING,
                 COMPENSATING,
                 COMPENSATION_FAILED -> true;

            default -> false;
        };
    }

    private static void validateCreateArguments(
            String transferCode,
            UUID sourceWalletId,
            UUID targetWalletId,
            UUID sourceOwnerKeycloakUserId,
            UUID targetOwnerKeycloakUserId,
            BigDecimal amount,
            String currency,
            UUID requestIdempotencyKey,
            String requestHash
    ) {
        if (
                transferCode == null
                        || transferCode.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "transferCode is required"
            );
        }

        Objects.requireNonNull(
                sourceWalletId,
                "sourceWalletId is required"
        );

        Objects.requireNonNull(
                targetWalletId,
                "targetWalletId is required"
        );

        Objects.requireNonNull(
                sourceOwnerKeycloakUserId,
                "sourceOwnerKeycloakUserId is required"
        );

        Objects.requireNonNull(
                targetOwnerKeycloakUserId,
                "targetOwnerKeycloakUserId is required"
        );

        Objects.requireNonNull(
                amount,
                "amount is required"
        );

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "currency is required"
            );
        }

        if (currency.trim().length() != 3) {
            throw new IllegalArgumentException(
                    "currency must contain exactly 3 characters"
            );
        }

        Objects.requireNonNull(
                requestIdempotencyKey,
                "requestIdempotencyKey is required"
        );

        if (
                requestHash == null
                        || requestHash.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "requestHash is required"
            );
        }

        if (requestHash.trim().length() != 64) {
            throw new IllegalArgumentException(
                    "requestHash must be a SHA-256 hexadecimal value"
            );
        }
    }

    private static BigDecimal normalizeAmount(
            BigDecimal amount
    ) {
        try {
            BigDecimal normalized =
                    amount.setScale(
                            MONEY_SCALE,
                            RoundingMode.UNNECESSARY
                    );

            if (normalized.signum() <= 0) {
                throw new IllegalArgumentException(
                        "amount must be greater than zero"
                );
            }

            return normalized;

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "amount supports at most 4 decimal places",
                    exception
            );
        }
    }

    private static String normalizeNullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isBlank()
                ? null
                : normalized;
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

    private static int safeValue(Integer value) {
        return value != null ? value : 0;
    }

    public boolean hasSourceReservation() {
        return sourceReservationId != null;
    }

    public boolean hasSourceReserveResult() {
        return sourceReservationId != null
                && sourceReserveTransactionId != null;
    }

    public boolean hasTargetCreditResult() {
        return targetCreditTransactionId != null;
    }

    public boolean hasSourceFinalizeResult() {
        return sourceFinalizeTransactionId != null;
    }

    public boolean hasSourceReleaseResult() {
        return sourceReleasedAt != null;
    }
}

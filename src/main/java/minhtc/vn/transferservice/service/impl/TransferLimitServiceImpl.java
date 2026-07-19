package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.config.TransferLimitProperties;
import minhtc.vn.transferservice.domain.TransferDailyLimitUsage;
import minhtc.vn.transferservice.domain.TransferLimitReservation;
import minhtc.vn.transferservice.enums.LimitReservationStatus;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.exception.InvalidTransferAmountException;
import minhtc.vn.transferservice.exception.TransferLimitExceededException;
import minhtc.vn.transferservice.exception.TransferValidationException;
import minhtc.vn.transferservice.repository.TransferDailyLimitUsageRepository;
import minhtc.vn.transferservice.repository.TransferLimitReservationRepository;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.service.TransferLimitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferLimitServiceImpl
        implements TransferLimitService {

    /**
     * Các trạng thái được tính vào hạn mức trong ngày.
     *
     * <p>Không chỉ tính COMPLETED vì transfer đang xử lý cũng phải chiếm
     * hạn mức, tránh người dùng tạo nhiều giao dịch đồng thời để vượt
     * daily limit.</p>
     */
    private static final List<TransferStatus> COUNTED_STATUSES =
            List.of(
                    TransferStatus.OTP_VERIFIED,
                    TransferStatus.SOURCE_RESERVE_PENDING,
                    TransferStatus.SOURCE_RESERVED,
                    TransferStatus.TARGET_CREDIT_PENDING,
                    TransferStatus.TARGET_CREDITED,
                    TransferStatus.SOURCE_FINALIZE_PENDING,
                    TransferStatus.SOURCE_FINALIZED,
                    TransferStatus.COMPLETED,
                    TransferStatus.COMPENSATION_PENDING,
                    TransferStatus.COMPENSATING,
                    TransferStatus.COMPENSATION_FAILED,
                    TransferStatus.MANUAL_REVIEW
            );

    private static final String SUPPORTED_CURRENCY = "VND";

    private final TransferLimitProperties properties;

    private final TransferDailyLimitUsageRepository
            dailyUsageRepository;

    private final TransferLimitReservationRepository
            reservationRepository;

    private final TransferRepository transferRepository;

    private final Clock clock;

    @Override
    public void validateAmount(BigDecimal amount) {
        BigDecimal normalizedAmount = normalizeAmount(amount);

        if (normalizedAmount.signum() <= 0) {
            throw new InvalidTransferAmountException(
                    "Transfer amount must be greater than zero"
            );
        }

        if (normalizedAmount.compareTo(
                properties.minAmount()
        ) < 0) {
            throw new InvalidTransferAmountException(
                    "Transfer amount is lower than minimum amount: "
                            + properties.minAmount()
            );
        }

        if (normalizedAmount.compareTo(
                properties.maxAmount()
        ) > 0) {
            throw new InvalidTransferAmountException(
                    "Transfer amount exceeds maximum amount: "
                            + properties.maxAmount()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void preCheckDailyLimit(
            UUID ownerKeycloakUserId,
            BigDecimal amount
    ) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        LocalDate usageDate = LocalDate.now(clock);

        BigDecimal currentUsage = dailyUsageRepository
                .findByOwnerKeycloakUserIdAndUsageDate(
                        ownerKeycloakUserId,
                        usageDate
                )
                .map(
                        TransferDailyLimitUsage
                                ::totalUsedAndReserved
                )
                .orElseGet(() ->
                        BigDecimal.ZERO.setScale(4)
                );

        assertWithinDailyLimit(
                currentUsage,
                normalizedAmount
        );
    }

    @Override
    @Transactional
    public void reserveDailyLimit(
            UUID transferId,
            UUID ownerKeycloakUserId,
            BigDecimal amount
    ) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        LocalDate usageDate = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        /*
         * Bảo đảm row daily usage tồn tại.
         *
         * ON CONFLICT DO NOTHING giúp nhiều request đồng thời không
         * gặp unique constraint exception.
         */
        dailyUsageRepository.insertIfAbsent(
                UUID.randomUUID(),
                ownerKeycloakUserId,
                usageDate,
                now
        );

        /*
         * Lock theo owner và ngày.
         *
         * Mọi request chuyển tiền trong cùng ngày của user này sẽ
         * tuần tự cập nhật hạn mức, tránh race condition.
         */
        TransferDailyLimitUsage usage =
                dailyUsageRepository.findForUpdate(
                        ownerKeycloakUserId,
                        usageDate
                ).orElseThrow(() ->
                        new IllegalStateException(
                                "Daily limit usage not found"
                        )
                );

        /*
         * Kiểm tra lại sau khi đã lock để xử lý idempotency.
         */
        TransferLimitReservation existing =
                reservationRepository
                        .findByTransferIdForUpdate(transferId)
                        .orElse(null);

        if (existing != null) {
            validateExistingReservation(
                    existing,
                    ownerKeycloakUserId,
                    normalizedAmount
            );

            if (
                    existing.getStatus()
                            == LimitReservationStatus.RESERVED
                            || existing.getStatus()
                            == LimitReservationStatus.COMPLETED
            ) {
                return;
            }

            throw new IllegalStateException(
                    "Released limit reservation cannot be reserved again"
            );
        }

        BigDecimal currentUsage =
                usage.totalUsedAndReserved();

        assertWithinDailyLimit(
                currentUsage,
                normalizedAmount
        );

        usage.reserve(normalizedAmount);

        reservationRepository.save(
                TransferLimitReservation.reserve(
                        transferId,
                        ownerKeycloakUserId,
                        usageDate,
                        normalizedAmount
                )
        );

        dailyUsageRepository.save(usage);
    }

    @Override
    @Transactional
    public void completeDailyLimit(UUID transferId) {
        /*
         * Đọc trước để biết owner và usageDate.
         * Sau đó lock daily usage trước, reservation sau.
         *
         * Các method nên duy trì cùng thứ tự lock để hạn chế deadlock.
         */
        TransferLimitReservation snapshot =
                reservationRepository
                        .findByTransferId(transferId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Limit reservation not found for transfer: "
                                                + transferId
                                )
                        );

        TransferDailyLimitUsage usage =
                dailyUsageRepository.findForUpdate(
                        snapshot.getOwnerKeycloakUserId(),
                        snapshot.getUsageDate()
                ).orElseThrow(() ->
                        new IllegalStateException(
                                "Daily limit usage not found"
                        )
                );

        TransferLimitReservation reservation =
                reservationRepository
                        .findByTransferIdForUpdate(transferId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Limit reservation disappeared"
                                )
                        );

        if (
                reservation.getStatus()
                        == LimitReservationStatus.COMPLETED
        ) {
            return;
        }

        if (
                reservation.getStatus()
                        == LimitReservationStatus.RELEASED
        ) {
            throw new IllegalStateException(
                    "Released limit reservation cannot be completed"
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);

        usage.complete(
                reservation.getAmount()
        );

        reservation.markCompleted();

        dailyUsageRepository.save(usage);
        reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void releaseDailyLimit(UUID transferId) {
        TransferLimitReservation snapshot =
                reservationRepository
                        .findByTransferId(transferId)
                        .orElse(null);

        /*
         * Transfer có thể fail trước khi reserve limit.
         * Trong trường hợp đó release là no-op.
         */
        if (snapshot == null) {
            return;
        }

        TransferDailyLimitUsage usage =
                dailyUsageRepository.findForUpdate(
                        snapshot.getOwnerKeycloakUserId(),
                        snapshot.getUsageDate()
                ).orElseThrow(() ->
                        new IllegalStateException(
                                "Daily limit usage not found"
                        )
                );

        TransferLimitReservation reservation =
                reservationRepository
                        .findByTransferIdForUpdate(transferId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Limit reservation disappeared"
                                )
                        );

        if (
                reservation.getStatus()
                        == LimitReservationStatus.RELEASED
        ) {
            return;
        }

        if (
                reservation.getStatus()
                        == LimitReservationStatus.COMPLETED
        ) {
            /*
             * Không release completed limit trong compensation hiện tại.
             * Compensation chỉ xảy ra trước khi transfer hoàn tất.
             */
            throw new IllegalStateException(
                    "Completed limit reservation cannot be released"
            );
        }


        usage.release(
                reservation.getAmount()
        );

        reservation.markReleased();

        dailyUsageRepository.save(usage);
        reservationRepository.save(reservation);
    }

    /**
     * Kiểm tra giao dịch có vượt hạn mức hay không.
     *
     * @param ownerKeycloakUserId người thực hiện chuyển tiền
     * @param amount              số tiền chuyển
     * @param currency            loại tiền
     */
    @Override
    @Transactional(readOnly = true)
    public void validateTransferLimit(
            UUID ownerKeycloakUserId,
            BigDecimal amount,
            String currency
    ) {
        validateInput(
                ownerKeycloakUserId,
                amount,
                currency
        );

        validatePerTransactionLimit(amount);

        validateDailyLimit(
                ownerKeycloakUserId,
                amount,
                normalizeCurrency(currency)
        );
    }

    private void validateInput(
            UUID ownerKeycloakUserId,
            BigDecimal amount,
            String currency
    ) {
        if (ownerKeycloakUserId == null) {
            throw new TransferValidationException(
                    "TRANSFER_OWNER_REQUIRED",
                    "Transfer owner is required"
            );
        }

        if (amount == null) {
            throw new TransferValidationException(
                    "TRANSFER_AMOUNT_REQUIRED",
                    "Transfer amount is required"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferValidationException(
                    "INVALID_TRANSFER_AMOUNT",
                    "Transfer amount must be greater than zero"
            );
        }

        String normalizedCurrency =
                normalizeCurrency(currency);

        if (!SUPPORTED_CURRENCY.equals(normalizedCurrency)) {
            throw new TransferValidationException(
                    "UNSUPPORTED_CURRENCY",
                    "Unsupported transfer currency: "
                            + normalizedCurrency
            );
        }
    }

    private void validatePerTransactionLimit(
            BigDecimal amount
    ) {
        if (amount.compareTo(properties.minAmount()) < 0) {
            throw new TransferValidationException(
                    "BELOW_MIN_TRANSFER_AMOUNT",
                    "Minimum transfer amount is "
                            + properties.minAmount().toPlainString()
                            + " VND"
            );
        }

        if (amount.compareTo(properties.maxAmount()) > 0) {
            throw new TransferValidationException(
                    "TRANSFER_LIMIT_EXCEEDED",
                    "Maximum amount per transfer is "
                            + properties.maxAmount().toPlainString()
                            + " VND"
            );
        }
    }

    private void validateDailyLimit(
            UUID ownerKeycloakUserId,
            BigDecimal requestedAmount,
            String currency
    ) {
        LocalDate today =
                LocalDate.now(clock);

        LocalDateTime startOfDay =
                today.atStartOfDay();

        LocalDateTime endOfDay =
                today.plusDays(1).atStartOfDay();

        BigDecimal transferredToday =
                transferRepository.sumAmountForDailyLimit(
                        ownerKeycloakUserId,
                        currency,
                        COUNTED_STATUSES,
                        startOfDay,
                        endOfDay
                );

        BigDecimal currentDailyAmount =
                transferredToday != null
                        ? transferredToday
                        : BigDecimal.ZERO;

        BigDecimal amountAfterTransfer =
                currentDailyAmount.add(requestedAmount);

        if (
                amountAfterTransfer.compareTo(
                        properties.dailyOutgoingLimit()
                ) > 0
        ) {
            BigDecimal remainingAmount =
                    properties.dailyOutgoingLimit()
                            .subtract(currentDailyAmount)
                            .max(BigDecimal.ZERO);

            throw new TransferValidationException(
                    "DAILY_TRANSFER_LIMIT_EXCEEDED",
                    "Daily transfer limit exceeded. Remaining amount: "
                            + remainingAmount.toPlainString()
                            + " "
                            + currency
            );
        }
    }

    private String normalizeCurrency(
            String currency
    ) {
        if (currency == null || currency.isBlank()) {
            return SUPPORTED_CURRENCY;
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private void assertWithinDailyLimit(
            BigDecimal currentUsage,
            BigDecimal requestedAmount
    ) {
        BigDecimal totalAfterRequest =
                currentUsage.add(requestedAmount);

        if (
                totalAfterRequest.compareTo(
                        properties.dailyOutgoingLimit()
                ) > 0
        ) {
            throw new TransferLimitExceededException(
                    properties.dailyOutgoingLimit(),
                    currentUsage,
                    requestedAmount
            );
        }
    }

    private void validateExistingReservation(
            TransferLimitReservation existing,
            UUID ownerKeycloakUserId,
            BigDecimal amount
    ) {
        if (
                !existing.getOwnerKeycloakUserId()
                        .equals(ownerKeycloakUserId)
        ) {
            throw new IllegalStateException(
                    "Transfer limit reservation owner mismatch"
            );
        }

        if (existing.getAmount().compareTo(amount) != 0) {
            throw new IllegalStateException(
                    "Transfer limit reservation amount mismatch"
            );
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidTransferAmountException(
                    "Transfer amount is required"
            );
        }

        try {
            return amount.setScale(
                    4,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new InvalidTransferAmountException(
                    "Transfer amount supports at most 4 decimal places"
            );
        }
    }
}
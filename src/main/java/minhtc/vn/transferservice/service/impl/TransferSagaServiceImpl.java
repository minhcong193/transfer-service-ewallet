package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.TransferTransitionContext;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.enums.WalletReservationStatus;
import minhtc.vn.transferservice.enums.WalletTransactionStatus;
import minhtc.vn.transferservice.exception.InvalidTransferStateException;
import minhtc.vn.transferservice.client.WalletClient;
import minhtc.vn.transferservice.dto.integration.wallet.CreditWalletRequest;
import minhtc.vn.transferservice.dto.integration.wallet.FinalizeReservationRequest;
import minhtc.vn.transferservice.dto.integration.wallet.ReleaseReservationRequest;
import minhtc.vn.transferservice.dto.integration.wallet.ReserveWalletRequest;
import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;
import minhtc.vn.transferservice.exception.WalletClientException;
import minhtc.vn.transferservice.exception.WalletClientTimeoutException;
import minhtc.vn.transferservice.service.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSagaServiceImpl
        implements TransferSagaService {

    private static final Duration DEFAULT_RETRY_DELAY =
            Duration.ofSeconds(30);

    private final WalletClient walletClient;

    private final TransferSagaPersistenceService persistenceService;

    private final TransferSagaResultService sagaResultService;

    private final TransferStateMachineService stateMachineService;

    private final TransferLimitService transferLimitService;

    private final Clock clock;

    /**
     * Chạy Saga từ trạng thái hiện tại.
     *
     * <p>Method này có thể được gọi sau khi confirm OTP hoặc bởi
     * Recovery Scheduler. Vì mỗi bước sử dụng commandId cố định trong
     * Transfer nên việc gọi lại là idempotent.</p>
     */
    @Override
    public void execute(UUID transferId) {
        Transfer transfer =
                persistenceService.getTransfer(transferId);

        switch (transfer.getStatus()) {
            case OTP_VERIFIED,
                 SOURCE_RESERVE_PENDING -> {
                reserveSourceWallet(transferId);
            }

            case SOURCE_RESERVED,
                 TARGET_CREDIT_PENDING -> {
                creditTargetWallet(transferId);
            }

            case TARGET_CREDITED,
                 SOURCE_FINALIZE_PENDING -> {
                finalizeSourceReservation(transferId);
            }

            case TARGET_CREDIT_FAILED,
                 COMPENSATION_PENDING,
                 COMPENSATING,
                 COMPENSATION_FAILED -> {
                compensateSourceReservation(
                        transferId,
                        "RECOVERY_COMPENSATION"
                );
            }

            case COMPLETED,
                 FAILED,
                 COMPENSATED,
                 CANCELLED,
                 MANUAL_REVIEW -> {
                log.info(
                        "Transfer {} is already terminal. status={}",
                        transferId,
                        transfer.getStatus()
                );
            }

            default -> throw invalidState(
                    transfer,
                    "Transfer cannot execute Saga from current status"
            );
        }
    }

    /**
     * Bước 1: Reserve tiền ở ví nguồn.
     */
    @Override
    public void reserveSourceWallet(UUID transferId) {
        Transfer transfer =
                persistenceService.getTransfer(transferId);

        if (transfer.getStatus() == TransferStatus.SOURCE_RESERVED) {
            creditTargetWallet(transferId);
            return;
        }

        if (
                transfer.getStatus() != TransferStatus.OTP_VERIFIED
                        && transfer.getStatus()
                        != TransferStatus.SOURCE_RESERVE_PENDING
        ) {
            throw invalidState(
                    transfer,
                    "Transfer is not ready for source wallet reservation"
            );
        }

        if (
                transfer.getStatus()
                        != TransferStatus.SOURCE_RESERVE_PENDING
        ) {
            stateMachineService.transition(
                    transferId,
                    TransferStatus.SOURCE_RESERVE_PENDING,
                    TransferTransitionContext.saga(
                            "Calling Wallet Service to reserve source wallet",
                            transfer.getCorrelationId()
                    )
            );
        }

        transfer =
                persistenceService.getTransfer(transferId);

        ReserveWalletRequest request =
                buildReserveRequest(transfer);

        try {
            WalletReservationResult result =
                    walletClient.reserve(
                            transfer.getSourceWalletId(),
                            request
                    );

            validateReserveResult(
                    transfer,
                    result
            );

            if (isReserveSuccessful(result)) {
                LocalDateTime reservedAt =
                        result.createdAt() != null
                                ? result.createdAt()
                                : now();

                sagaResultService.markSourceReserved(
                        transferId,
                        result,
                        reservedAt
                );

                creditTargetWallet(transferId);
                return;
            }

            handleReserveBusinessFailure(
                    transfer,
                    result
            );

        } catch (WalletClientTimeoutException exception) {
            scheduleRecovery(
                    transferId,
                    "SOURCE_RESERVE_TIMEOUT",
                    exception.getMessage()
            );

        } catch (WalletClientException exception) {
            if (exception.isRetryable()) {
                scheduleRecovery(
                        transferId,
                        exception.getErrorCode(),
                        exception.getMessage()
                );
                return;
            }

            handleReserveClientRejection(
                    transfer,
                    exception
            );

        } catch (RuntimeException exception) {
            scheduleRecovery(
                    transferId,
                    "SOURCE_RESERVE_UNEXPECTED_ERROR",
                    exception.getMessage()
            );
        }
    }

    /**
     * Bước 2: Credit tiền vào ví đích.
     */
    @Override
    public void creditTargetWallet(UUID transferId) {
        Transfer transfer =
                persistenceService.getTransfer(transferId);

        if (transfer.getStatus() == TransferStatus.TARGET_CREDITED) {
            finalizeSourceReservation(transferId);
            return;
        }

        if (
                transfer.getStatus() != TransferStatus.SOURCE_RESERVED
                        && transfer.getStatus()
                        != TransferStatus.TARGET_CREDIT_PENDING
        ) {
            throw invalidState(
                    transfer,
                    "Transfer is not ready for target wallet credit"
            );
        }

        if (
                transfer.getStatus()
                        != TransferStatus.TARGET_CREDIT_PENDING
        ) {
            stateMachineService.transition(
                    transferId,
                    TransferStatus.TARGET_CREDIT_PENDING,
                    TransferTransitionContext.saga(
                            "Calling Wallet Service to credit target wallet",
                            transfer.getCorrelationId()
                    )
            );
        }

        transfer =
                persistenceService.getTransfer(transferId);

        CreditWalletRequest request =
                buildCreditRequest(transfer);

        try {
            WalletCreditResult result =
                    walletClient.credit(
                            transfer.getTargetWalletId(),
                            request
                    );

            validateCreditResult(
                    transfer,
                    result
            );

            if (isCreditSuccessful(result)) {
                LocalDateTime creditedAt =
                        now();

                sagaResultService.markTargetCredited(
                        transferId,
                        result,
                        creditedAt
                );

                finalizeSourceReservation(transferId);
                return;
            }

            handleCreditBusinessFailure(
                    transfer,
                    result
            );

        } catch (WalletClientTimeoutException exception) {
            /*
             * Không được compensation ngay.
             *
             * Credit có thể đã thành công ở Wallet Service nhưng response
             * bị timeout. Recovery phải query bằng targetCreditCommandId.
             */
            scheduleRecovery(
                    transferId,
                    "TARGET_CREDIT_TIMEOUT",
                    exception.getMessage()
            );

        } catch (WalletClientException exception) {
            if (exception.isRetryable()) {
                scheduleRecovery(
                        transferId,
                        exception.getErrorCode(),
                        exception.getMessage()
                );
                return;
            }

            handleCreditClientRejection(
                    transfer,
                    exception
            );

        } catch (RuntimeException exception) {
            scheduleRecovery(
                    transferId,
                    "TARGET_CREDIT_UNEXPECTED_ERROR",
                    exception.getMessage()
            );
        }
    }

    /**
     * Bước 3: Finalize reservation ở ví nguồn.
     */
    @Override
    public void finalizeSourceReservation(UUID transferId) {
        Transfer transfer =
                persistenceService.getTransfer(transferId);

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            return;
        }

        if (
                transfer.getStatus() != TransferStatus.TARGET_CREDITED
                        && transfer.getStatus()
                        != TransferStatus.SOURCE_FINALIZE_PENDING
        ) {
            throw invalidState(
                    transfer,
                    "Transfer is not ready for source reservation finalization"
            );
        }

        if (transfer.getSourceReservationId() == null) {
            moveToManualReview(
                    transfer,
                    "SOURCE_RESERVATION_ID_MISSING",
                    "Cannot finalize because sourceReservationId is null"
            );
            return;
        }

        if (
                transfer.getStatus()
                        != TransferStatus.SOURCE_FINALIZE_PENDING
        ) {
            stateMachineService.transition(
                    transferId,
                    TransferStatus.SOURCE_FINALIZE_PENDING,
                    TransferTransitionContext.saga(
                            "Calling Wallet Service to finalize source reservation",
                            transfer.getCorrelationId()
                    )
            );
        }

        transfer =
                persistenceService.getTransfer(transferId);

        FinalizeReservationRequest request =
                buildFinalizeRequest(transfer);

        try {
            WalletFinalizeResult result =
                    walletClient.finalizeReservation(
                            transfer.getSourceWalletId(),
                            transfer.getSourceReservationId(),
                            request
                    );

            validateFinalizeResult(
                    transfer,
                    result
            );

            if (isFinalizeSuccessful(result)) {
                LocalDateTime finalizedAt =
                        result.finalizedAt() != null
                                ? result.finalizedAt()
                                : now();

                sagaResultService.markCompleted(
                        transferId,
                        result,
                        finalizedAt
                );

//                transferLimitService.completeReservation(
//                        transferId
//                );

                return;
            }

            handleFinalizeBusinessFailure(
                    transfer,
                    result
            );

        } catch (WalletClientTimeoutException exception) {
            /*
             * Target wallet đã được credit.
             * Không release source reservation.
             * Recovery phải query sourceFinalizeCommandId.
             */
            scheduleRecovery(
                    transferId,
                    "SOURCE_FINALIZE_TIMEOUT",
                    exception.getMessage()
            );

        } catch (WalletClientException exception) {
            /*
             * Kể cả 4xx cũng không release ở bước finalize.
             * Vì target wallet đã nhận tiền, finalize là bước phải retry
             * hoặc manual review.
             */
            scheduleRecovery(
                    transferId,
                    exception.getErrorCode(),
                    exception.getMessage()
            );

        } catch (RuntimeException exception) {
            scheduleRecovery(
                    transferId,
                    "SOURCE_FINALIZE_UNEXPECTED_ERROR",
                    exception.getMessage()
            );
        }
    }

    /**
     * Compensation: release reservation ở ví nguồn.
     *
     * <p>Chỉ gọi bước này khi reserve source đã thành công nhưng credit
     * target thất bại chắc chắn.</p>
     */
    @Override
    public void compensateSourceReservation(
            UUID transferId,
            String reason
    ) {
        Transfer transfer =
                persistenceService.getTransfer(transferId);

        if (transfer.getStatus() == TransferStatus.COMPENSATED) {
            return;
        }

        if (transfer.getSourceReservationId() == null) {
            moveToManualReview(
                    transfer,
                    "SOURCE_RESERVATION_ID_MISSING",
                    "Cannot compensate because sourceReservationId is null"
            );
            return;
        }

        if (
                transfer.getStatus() != TransferStatus.TARGET_CREDIT_FAILED
                        && transfer.getStatus()
                        != TransferStatus.COMPENSATION_PENDING
                        && transfer.getStatus()
                        != TransferStatus.COMPENSATING
                        && transfer.getStatus()
                        != TransferStatus.COMPENSATION_FAILED
        ) {
            throw invalidState(
                    transfer,
                    "Transfer is not ready for compensation"
            );
        }

        if (
                transfer.getStatus()
                        != TransferStatus.COMPENSATING
        ) {
            stateMachineService.transition(
                    transferId,
                    TransferStatus.COMPENSATING,
                    TransferTransitionContext.saga(
                            "Calling Wallet Service to release source reservation",
                            transfer.getCorrelationId()
                    )
            );
        }

        transfer =
                persistenceService.getTransfer(transferId);

        ReleaseReservationRequest request =
                buildReleaseRequest(
                        transfer,
                        reason
                );

        try {
            WalletReleaseResult result =
                    walletClient.releaseReservation(
                            transfer.getSourceWalletId(),
                            transfer.getSourceReservationId(),
                            request
                    );

            validateReleaseResult(
                    transfer,
                    result
            );

            if (isReleaseSuccessful(result)) {
                LocalDateTime releasedAt =
                        result.releasedAt() != null
                                ? result.releasedAt()
                                : now();

                sagaResultService.markCompensated(
                        transferId,
                        result,
                        releasedAt
                );

//                transferLimitService.releaseReservation(
//                        transferId
//                );

                return;
            }

            handleReleaseBusinessFailure(
                    transfer,
                    result
            );

        } catch (WalletClientTimeoutException exception) {
            scheduleCompensationRetry(
                    transfer,
                    "SOURCE_RELEASE_TIMEOUT",
                    exception.getMessage()
            );

        } catch (WalletClientException exception) {
            scheduleCompensationRetry(
                    transfer,
                    exception.getErrorCode(),
                    exception.getMessage()
            );

        } catch (RuntimeException exception) {
            scheduleCompensationRetry(
                    transfer,
                    "SOURCE_RELEASE_UNEXPECTED_ERROR",
                    exception.getMessage()
            );
        }
    }

    private ReserveWalletRequest buildReserveRequest(
            Transfer transfer
    ) {
        return new ReserveWalletRequest(
                transfer.getSourceReserveCommandId(),
                transfer.getId(),
                transfer.getTargetWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCorrelationId()
        );
    }

    private CreditWalletRequest buildCreditRequest(
            Transfer transfer
    ) {
        return new CreditWalletRequest(
                transfer.getTargetCreditCommandId(),
                transfer.getId(),
                transfer.getSourceWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCorrelationId()
        );
    }

    private FinalizeReservationRequest buildFinalizeRequest(
            Transfer transfer
    ) {
        return new FinalizeReservationRequest(
                transfer.getSourceFinalizeCommandId(),
                transfer.getId(),
                transfer.getDescription(),
                transfer.getCorrelationId()
        );
    }

    private ReleaseReservationRequest buildReleaseRequest(
            Transfer transfer,
            String reason
    ) {
        return new ReleaseReservationRequest(
                transfer.getSourceReleaseCommandId(),
                transfer.getId(),
                reason,
                transfer.getCorrelationId()
        );
    }

    private void validateReserveResult(
            Transfer transfer,
            WalletReservationResult result
    ) {
        requireCommandId(
                transfer.getSourceReserveCommandId(),
                result.commandId(),
                "reserve"
        );

        requireTransferId(
                transfer.getId(),
                result.transferId(),
                "reserve"
        );

        requireWalletId(
                transfer.getSourceWalletId(),
                result.walletId(),
                "reserve"
        );

        if (
                result.counterpartyWalletId() != null
                        && !transfer.getTargetWalletId()
                        .equals(result.counterpartyWalletId())
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "RESERVE_COUNTERPARTY_WALLET_MISMATCH",
                    "Reserve result counterparty wallet does not match transfer target wallet"
            );
        }

        validateMoney(
                transfer,
                result.amount(),
                result.currency(),
                "reserve"
        );
    }

    private void validateCreditResult(
            Transfer transfer,
            WalletCreditResult result
    ) {
        requireCommandId(
                transfer.getTargetCreditCommandId(),
                result.commandId(),
                "credit"
        );

        requireTransferId(
                transfer.getId(),
                result.transferId(),
                "credit"
        );

        requireWalletId(
                transfer.getTargetWalletId(),
                result.walletId(),
                "credit"
        );

        validateMoney(
                transfer,
                result.amount(),
                result.currency(),
                "credit"
        );
    }

    private void validateFinalizeResult(
            Transfer transfer,
            WalletFinalizeResult result
    ) {
        requireCommandId(
                transfer.getSourceFinalizeCommandId(),
                result.commandId(),
                "finalize"
        );

        requireTransferId(
                transfer.getId(),
                result.transferId(),
                "finalize"
        );

        requireWalletId(
                transfer.getSourceWalletId(),
                result.walletId(),
                "finalize"
        );

        if (
                result.reservationId() == null
                        || !transfer.getSourceReservationId()
                        .equals(result.reservationId())
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "FINALIZE_RESERVATION_ID_MISMATCH",
                    "Finalize result reservationId does not match Transfer sourceReservationId"
            );
        }

        validateMoney(
                transfer,
                result.amount(),
                result.currency(),
                "finalize"
        );
    }

    private void validateReleaseResult(
            Transfer transfer,
            WalletReleaseResult result
    ) {
        requireCommandId(
                transfer.getSourceReleaseCommandId(),
                result.commandId(),
                "release"
        );

        requireTransferId(
                transfer.getId(),
                result.transferId(),
                "release"
        );

        requireWalletId(
                transfer.getSourceWalletId(),
                result.walletId(),
                "release"
        );

        if (
                result.reservationId() == null
                        || !transfer.getSourceReservationId()
                        .equals(result.reservationId())
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "RELEASE_RESERVATION_ID_MISMATCH",
                    "Release result reservationId does not match Transfer sourceReservationId"
            );
        }

        validateMoney(
                transfer,
                result.amount(),
                result.currency(),
                "release"
        );
    }

    private boolean isReserveSuccessful(
            WalletReservationResult result
    ) {
        return result.failureCode() == null
                && result.reservationId() != null
                && result.walletTransactionId() != null
                && result.reservationStatus()
                == WalletReservationStatus.HELD
                && result.transactionStatus()
                == WalletTransactionStatus.COMPLETED;
    }

    private boolean isCreditSuccessful(
            WalletCreditResult result
    ) {
        return result.failureCode() == null
                && result.walletTransactionId() != null
                && result.status() != null
                && "COMPLETED".equalsIgnoreCase(
                result.status()
        );
    }

    private boolean isFinalizeSuccessful(
            WalletFinalizeResult result
    ) {
        return result.failureCode() == null
                && result.walletTransactionId() != null
                && result.reservationStatus()
                == WalletReservationStatus.FINALIZED
                && result.transactionStatus()
                == WalletTransactionStatus.COMPLETED;
    }

    private boolean isReleaseSuccessful(
            WalletReleaseResult result
    ) {
        return result.failureCode() == null
                && result.reservationStatus()
                == WalletReservationStatus.RELEASED
                && result.transactionStatus()
                == WalletTransactionStatus.COMPLETED;
    }

    private void handleReserveBusinessFailure(
            Transfer transfer,
            WalletReservationResult result
    ) {
        String failureCode =
                result.failureCode() != null
                        ? result.failureCode().name()
                        : "SOURCE_RESERVE_REJECTED";

        String failureMessage =
                result.failureMessage() != null
                        ? result.failureMessage()
                        : "Wallet Service rejected source reservation";

        sagaResultService.markSourceReserveFailed(
                transfer.getId(),
                failureCode,
                failureMessage
        );

//        transferLimitService.releaseReservation(
//                transfer.getId()
//        );
    }

    private void handleReserveClientRejection(
            Transfer transfer,
            WalletClientException exception
    ) {
        sagaResultService.markSourceReserveFailed(
                transfer.getId(),
                exception.getErrorCode(),
                exception.getMessage()
        );

//        transferLimitService.releaseReservation(
//                transfer.getId()
//        );
    }

    private void handleCreditBusinessFailure(
            Transfer transfer,
            WalletCreditResult result
    ) {
        String failureCode =
                result.failureCode() != null
                        ? result.failureCode().name()
                        : "TARGET_CREDIT_REJECTED";

        String failureMessage =
                result.failureMessage() != null
                        ? result.failureMessage()
                        : "Wallet Service rejected target credit";

        sagaResultService.markTargetCreditFailed(
                transfer.getId(),
                failureCode,
                failureMessage
        );

        compensateSourceReservation(
                transfer.getId(),
                failureCode
        );
    }

    private void handleCreditClientRejection(
            Transfer transfer,
            WalletClientException exception
    ) {
        /*
         * Command conflict là trường hợp không chắc chắn.
         * Không nên compensation tự động vì có thể commandId đã bị dùng
         * trong tình huống dữ liệu không nhất quán.
         */
        if ("WALLET_COMMAND_CONFLICT".equals(exception.getErrorCode())) {
            moveToManualReview(
                    transfer,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
            return;
        }

        if (exception.isBusinessRejection()) {
            sagaResultService.markTargetCreditFailed(
                    transfer.getId(),
                    exception.getErrorCode(),
                    exception.getMessage()
            );

            compensateSourceReservation(
                    transfer.getId(),
                    exception.getErrorCode()
            );

            return;
        }

        scheduleRecovery(
                transfer.getId(),
                exception.getErrorCode(),
                exception.getMessage()
        );
    }

    private void handleFinalizeBusinessFailure(
            Transfer transfer,
            WalletFinalizeResult result
    ) {
        String failureCode =
                result.failureCode() != null
                        ? result.failureCode().name()
                        : "SOURCE_FINALIZE_REJECTED";

        String failureMessage =
                result.failureMessage() != null
                        ? result.failureMessage()
                        : "Wallet Service rejected source reservation finalization";

        /*
         * Target wallet đã được credit.
         * Không được release source reservation ở đây.
         */
        scheduleRecovery(
                transfer.getId(),
                failureCode,
                failureMessage
        );
    }

    private void handleReleaseBusinessFailure(
            Transfer transfer,
            WalletReleaseResult result
    ) {
        String failureCode =
                result.failureCode() != null
                        ? result.failureCode().name()
                        : "SOURCE_RELEASE_REJECTED";

        String failureMessage =
                result.failureMessage() != null
                        ? result.failureMessage()
                        : "Wallet Service rejected source reservation release";

        scheduleCompensationRetry(
                transfer,
                failureCode,
                failureMessage
        );
    }

    private void scheduleRecovery(
            UUID transferId,
            String errorCode,
            String errorMessage
    ) {
        LocalDateTime retryAt =
                now().plus(DEFAULT_RETRY_DELAY);

        persistenceService.update(
                transferId,
                transfer -> transfer.scheduleRecovery(
                        errorCode,
                        errorMessage,
                        retryAt
                )
        );

        log.warn(
                "Transfer {} scheduled for recovery. retryAt={}, code={}, message={}",
                transferId,
                retryAt,
                errorCode,
                errorMessage
        );
    }

    private void scheduleCompensationRetry(
            Transfer transfer,
            String errorCode,
            String errorMessage
    ) {
        LocalDateTime retryAt =
                now().plus(DEFAULT_RETRY_DELAY);

        sagaResultService.markCompensationFailed(
                transfer.getId(),
                errorCode,
                errorMessage,
                retryAt
        );

        log.error(
                "Transfer {} compensation scheduled for retry. retryAt={}, code={}, message={}",
                transfer.getId(),
                retryAt,
                errorCode,
                errorMessage
        );
    }

    private void moveToManualReview(
            Transfer transfer,
            String errorCode,
            String errorMessage
    ) {
        sagaResultService.markManualReview(
                transfer.getId(),
                errorCode,
                errorMessage
        );

        log.error(
                "Transfer {} moved to MANUAL_REVIEW. code={}, message={}",
                transfer.getId(),
                errorCode,
                errorMessage
        );
    }

    private void requireCommandId(
            UUID expected,
            UUID actual,
            String operation
    ) {
        if (actual == null || !expected.equals(actual)) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_COMMAND_ID_MISMATCH",
                    "Wallet " + operation
                            + " result commandId does not match request commandId"
            );
        }
    }

    private void requireTransferId(
            UUID expected,
            UUID actual,
            String operation
    ) {
        if (actual == null || !expected.equals(actual)) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_TRANSFER_ID_MISMATCH",
                    "Wallet " + operation
                            + " result transferId does not match Transfer ID"
            );
        }
    }

    private void requireWalletId(
            UUID expected,
            UUID actual,
            String operation
    ) {
        if (actual == null || !expected.equals(actual)) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_ID_MISMATCH",
                    "Wallet " + operation
                            + " result walletId does not match expected wallet"
            );
        }
    }

    private void validateMoney(
            Transfer transfer,
            BigDecimal resultAmount,
            String resultCurrency,
            String operation
    ) {
        if (
                resultAmount == null
                        || transfer.getAmount()
                        .compareTo(resultAmount) != 0
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_AMOUNT_MISMATCH",
                    "Wallet " + operation
                            + " result amount does not match Transfer amount"
            );
        }

        if (
                resultCurrency == null
                        || !transfer.getCurrency()
                        .equalsIgnoreCase(resultCurrency)
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_CURRENCY_MISMATCH",
                    "Wallet " + operation
                            + " result currency does not match Transfer currency"
            );
        }
    }

    private InvalidTransferStateException invalidState(
            Transfer transfer,
            String message
    ) {
        return new InvalidTransferStateException(
                transfer.getId(),
                transfer.getStatus(),
                message
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
